import {type ExtensionContext} from "@earendil-works/pi-coding-agent";
import * as crypto from "node:crypto";
import * as http from "node:http";
import {type Duplex} from "node:stream";
import {URL} from "node:url";
import {resolveStartupActivity} from "./status.ts";

const CONTROL_ENDPOINT_ENV = "AGENT_WORKBENCH_PI_CONTROL_WS_ENDPOINT";
const STATUS_TOKEN_ENV = "AGENT_WORKBENCH_PI_STATUS_TOKEN";
const CONTROL_ENDPOINT = process.env[CONTROL_ENDPOINT_ENV];
const STATUS_TOKEN = process.env[STATUS_TOKEN_ENV];

type AgentWorkbenchControlBridge = {
  setContext: (ctx: ExtensionContext) => void;
  close: () => void;
};

type AgentWorkbenchControlCommand = {
  type?: string;
  requestId?: string;
  sessionId?: string;
  entryId?: string;
  position?: string;
};

type AgentWorkbenchControlThread = {
  id: string;
  title: string;
  updatedAt: number;
  activity: string;
};

type AgentWorkbenchControlContext = ExtensionContext & {
  navigateTree?: (entryId: string) => Promise<void> | void;
  fork?: (
    entryId: string,
    options?: {
      position?: "at" | "after";
      withSession?: (ctx: ExtensionContext) => Promise<void> | void;
    },
  ) => Promise<{ cancelled?: boolean } | void> | { cancelled?: boolean } | void;
};

type AgentWorkbenchControlSocket = {
  sendText: (message: string) => boolean;
  close: () => void;
  isOpen: () => boolean;
};

export function startControlBridge(ctx: ExtensionContext): AgentWorkbenchControlBridge | undefined {
  if (CONTROL_ENDPOINT === undefined || STATUS_TOKEN === undefined) {
    return undefined;
  }

  let currentCtx = ctx;
  const socket = connectControlSocket({
    endpoint: CONTROL_ENDPOINT,
    token: STATUS_TOKEN,
    onOpen: () => sendSessionMessage("hello"),
    onText: (message) => void handleControlMessage(message),
  });

  const sendSessionMessage = (type: "hello" | "sessionState") => {
    const sessionId = currentCtx.sessionManager.getSessionId();
    const cwd = currentCtx.cwd;
    if (sessionId === undefined || cwd === undefined || !socket.isOpen()) {
      return;
    }
    socket.sendText(JSON.stringify({
      type,
      token: type === "hello" ? STATUS_TOKEN : undefined,
      sessionId,
      cwd,
      capabilities: resolveCapabilities(currentCtx),
      thread: createThreadSnapshot(currentCtx),
    }));
  };

  const handleControlMessage = async (message: string) => {
    const command = parseControlCommand(message);
    if (command === undefined) {
      return;
    }
    if (command.type === "navigateTree") {
      await handleNavigateTree(command);
    }
    else if (command.type === "forkFromEntry") {
      await handleForkFromEntry(command);
    }
  };

  const handleNavigateTree = async (command: AgentWorkbenchControlCommand) => {
    const requestId = command.requestId;
    try {
      const entryId = requireEntryId(command);
      requireCurrentSession(command);
      const controlCtx = currentCtx as AgentWorkbenchControlContext;
      if (typeof controlCtx.navigateTree !== "function") {
        throw new Error("Pi navigateTree is unavailable");
      }
      await controlCtx.navigateTree(entryId);
      sendResponse(requestId, {ok: true});
    }
    catch (error) {
      sendResponse(requestId, {ok: false, error: normalizeError(error)});
    }
  };

  const handleForkFromEntry = async (command: AgentWorkbenchControlCommand) => {
    const requestId = command.requestId;
    try {
      const entryId = requireEntryId(command);
      requireCurrentSession(command);
      const controlCtx = currentCtx as AgentWorkbenchControlContext;
      if (typeof controlCtx.fork !== "function") {
        throw new Error("Pi fork is unavailable");
      }

      let forkedThread: AgentWorkbenchControlThread | undefined;
      const result = await controlCtx.fork(entryId, {
        position: command.position === "after" ? "after" : "at",
        withSession: async (forkedCtx) => {
          currentCtx = forkedCtx;
          forkedThread = createThreadSnapshot(forkedCtx);
          sendSessionMessage("sessionState");
        },
      });
      if (result?.cancelled === true) {
        sendResponse(requestId, {ok: true, cancelled: true});
        return;
      }

      forkedThread ??= createThreadSnapshot(currentCtx);
      if (forkedThread === undefined) {
        throw new Error("Pi fork did not provide a replacement session");
      }
      sendResponse(requestId, {ok: true, thread: forkedThread});
    }
    catch (error) {
      sendResponse(requestId, {ok: false, error: normalizeError(error)});
    }
  };

  const sendResponse = (
    requestId: string | undefined,
    payload: { ok: boolean; cancelled?: boolean; error?: string; thread?: AgentWorkbenchControlThread },
  ) => {
    if (!socket.isOpen()) {
      return;
    }
    socket.sendText(JSON.stringify({type: "response", requestId, ...payload}));
  };

  const requireEntryId = (command: AgentWorkbenchControlCommand): string => {
    const entryId = command.entryId?.trim();
    if (entryId === undefined || entryId.length === 0) {
      throw new Error("Missing Pi tree entry id");
    }
    return entryId;
  };

  const requireCurrentSession = (command: AgentWorkbenchControlCommand) => {
    const sessionId = currentCtx.sessionManager.getSessionId();
    if (sessionId === undefined || command.sessionId !== sessionId) {
      throw new Error("Pi control command targets a stale session");
    }
  };

  return {
    setContext: (nextCtx) => {
      currentCtx = nextCtx;
      sendSessionMessage("sessionState");
    },
    close: () => socket.close(),
  };
}

function resolveCapabilities(ctx: ExtensionContext): { navigateTree: boolean; fork: boolean } {
  const controlCtx = ctx as AgentWorkbenchControlContext;
  return {
    navigateTree: typeof controlCtx.navigateTree === "function",
    fork: typeof controlCtx.fork === "function",
  };
}

function createThreadSnapshot(ctx: ExtensionContext): AgentWorkbenchControlThread | undefined {
  const sessionId = ctx.sessionManager.getSessionId();
  if (sessionId === undefined) {
    return undefined;
  }
  return {
    id: sessionId,
    title: ctx.sessionManager.getSessionName() ?? sessionId,
    updatedAt: Date.now(),
    activity: resolveStartupActivity(ctx),
  };
}

function parseControlCommand(message: string): AgentWorkbenchControlCommand | undefined {
  try {
    const value = JSON.parse(message);
    if (typeof value !== "object" || value === null) {
      return undefined;
    }
    return value as AgentWorkbenchControlCommand;
  }
  catch {
    return undefined;
  }
}

function normalizeError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function connectControlSocket(options: {
  endpoint: string;
  token: string;
  onOpen: () => void;
  onText: (message: string) => void;
}): AgentWorkbenchControlSocket {
  const endpoint = new URL(options.endpoint);
  let socket: Duplex | undefined;
  let open = false;
  let readBuffer = Buffer.alloc(0);
  const key = crypto.randomBytes(16).toString("base64");
  const request = http.request({
    protocol: endpoint.protocol === "wss:" ? "https:" : "http:",
    hostname: endpoint.hostname,
    port: endpoint.port,
    path: `${endpoint.pathname}${endpoint.search}`,
    method: "GET",
    headers: {
      "authorization": `Bearer ${options.token}`,
      "connection": "Upgrade",
      "upgrade": "websocket",
      "sec-websocket-key": key,
      "sec-websocket-version": "13",
    },
  });

  request.on("upgrade", (response, upgradedSocket, head) => {
    if (response.statusCode !== 101) {
      upgradedSocket.destroy();
      return;
    }
    socket = upgradedSocket;
    open = true;
    socket.on("data", (chunk: Buffer) => {
      readBuffer = Buffer.concat([readBuffer, chunk]);
      readBuffer = drainFrames(readBuffer, options.onText, (payload) => sendFrame(socket, 0xA, payload));
    });
    socket.on("close", () => {
      open = false;
    });
    socket.on("error", () => {
      open = false;
    });
    if (head.length > 0) {
      readBuffer = Buffer.concat([readBuffer, head]);
      readBuffer = drainFrames(readBuffer, options.onText, (payload) => sendFrame(socket, 0xA, payload));
    }
    options.onOpen();
  });
  request.on("response", (response) => {
    response.resume();
  });
  request.on("error", () => {
    open = false;
  });
  request.end();

  return {
    sendText: (message) => sendFrame(socket, 0x1, Buffer.from(message, "utf8")),
    close: () => {
      sendFrame(socket, 0x8, Buffer.alloc(0));
      socket?.end();
      open = false;
    },
    isOpen: () => open && socket !== undefined && !socket.destroyed,
  };
}

function drainFrames(
  input: Buffer,
  onText: (message: string) => void,
  onPing: (payload: Buffer) => void,
): Buffer {
  let offset = 0;
  while (offset + 2 <= input.length) {
    const first = input[offset];
    const second = input[offset + 1];
    const opcode = first & 0x0f;
    const masked = (second & 0x80) !== 0;
    let length = second & 0x7f;
    let headerLength = 2;
    if (length === 126) {
      if (offset + 4 > input.length) break;
      length = input.readUInt16BE(offset + 2);
      headerLength = 4;
    }
    else if (length === 127) {
      if (offset + 10 > input.length) break;
      const longLength = input.readBigUInt64BE(offset + 2);
      if (longLength > BigInt(Number.MAX_SAFE_INTEGER)) {
        return Buffer.alloc(0);
      }
      length = Number(longLength);
      headerLength = 10;
    }

    const maskLength = masked ? 4 : 0;
    const frameStart = offset + headerLength + maskLength;
    const frameEnd = frameStart + length;
    if (frameEnd > input.length) break;

    let payload = input.subarray(frameStart, frameEnd);
    if (masked) {
      const mask = input.subarray(offset + headerLength, frameStart);
      payload = Buffer.from(payload.map((value, index) => value ^ mask[index % 4]));
    }

    if (opcode === 0x1) {
      onText(payload.toString("utf8"));
    }
    else if (opcode === 0x8) {
      return Buffer.alloc(0);
    }
    else if (opcode === 0x9) {
      onPing(payload);
    }
    offset = frameEnd;
  }
  return input.subarray(offset);
}

function sendFrame(socket: Duplex | undefined, opcode: number, payload: Buffer): boolean {
  if (socket === undefined || socket.destroyed) {
    return false;
  }
  const headerLength = payload.length < 126 ? 2 : payload.length <= 0xffff ? 4 : 10;
  const frame = Buffer.alloc(headerLength + 4 + payload.length);
  frame[0] = 0x80 | opcode;
  if (payload.length < 126) {
    frame[1] = 0x80 | payload.length;
  }
  else if (payload.length <= 0xffff) {
    frame[1] = 0x80 | 126;
    frame.writeUInt16BE(payload.length, 2);
  }
  else {
    frame[1] = 0x80 | 127;
    frame.writeBigUInt64BE(BigInt(payload.length), 2);
  }
  const maskOffset = headerLength;
  const mask = crypto.randomBytes(4);
  mask.copy(frame, maskOffset);
  for (let index = 0; index < payload.length; index++) {
    frame[maskOffset + 4 + index] = payload[index] ^ mask[index % 4];
  }
  socket.write(frame);
  return true;
}
