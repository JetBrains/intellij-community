import {Type} from "@earendil-works/pi-ai";
import {defineTool, type ExtensionAPI, type ExtensionContext} from "@earendil-works/pi-coding-agent";
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
  requestTaskFolder: <T = unknown>(operation: string, args?: Record<string, unknown>) => Promise<T>;
  close: () => void;
};

type AgentWorkbenchControlMessageType =
  | "hello"
  | "sessionState"
  | "response"
  | "navigateTree"
  | "forkFromEntry"
  | "taskFolderRequest";

type AgentWorkbenchControlSessionMessageType = "hello" | "sessionState";
type AgentWorkbenchControlRequestType = "taskFolderRequest";

type AgentWorkbenchControlCommand = {
  type?: AgentWorkbenchControlMessageType;
  requestId?: string;
  sessionId?: string;
  cwd?: string;
  entryId?: string;
  position?: string;
  ok?: boolean;
  cancelled?: boolean;
  error?: string;
  operation?: string;
  arguments?: Record<string, unknown>;
  result?: unknown;
};

type AgentWorkbenchControlThread = {
  id: string;
  title: string;
  updatedAt: number;
  activity: string;
};

type AgentWorkbenchTaskFolder = {
  path: string;
  id: string;
  name: string;
  status: string;
  metadata?: Record<string, string>;
  createdAt?: number;
  updatedAt?: number;
};

type AgentWorkbenchTaskFolderThread = {
  path: string;
  provider: string;
  threadId: string;
  folderId: string;
  assignedAt?: number;
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
  const pendingRequests = new Map<string, {
    resolve: (command: AgentWorkbenchControlCommand) => void;
    reject: (error: Error) => void;
    timeout: NodeJS.Timeout;
  }>();
  const socket = connectControlSocket({
    endpoint: CONTROL_ENDPOINT,
    token: STATUS_TOKEN,
    onOpen: () => sendSessionMessage("hello"),
    onText: (message) => void handleControlMessage(message),
  });

  const sendSessionMessage = (type: AgentWorkbenchControlSessionMessageType) => {
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
    if (command.type === "response") {
      handleResponse(command);
    }
    else if (command.type === "navigateTree") {
      await handleNavigateTree(command);
    }
    else if (command.type === "forkFromEntry") {
      await handleForkFromEntry(command);
    }
  };

  const handleResponse = (command: AgentWorkbenchControlCommand) => {
    const requestId = command.requestId;
    if (requestId === undefined) {
      return;
    }
    const pending = pendingRequests.get(requestId);
    if (pending === undefined) {
      return;
    }
    pendingRequests.delete(requestId);
    clearTimeout(pending.timeout);
    pending.resolve(command);
  };

  const sendRequest = (
    type: AgentWorkbenchControlRequestType,
    payload: Partial<AgentWorkbenchControlCommand> = {},
  ): Promise<AgentWorkbenchControlCommand> => {
    const sessionId = currentCtx.sessionManager.getSessionId();
    const cwd = currentCtx.cwd;
    if (sessionId === undefined || cwd === undefined || !socket.isOpen()) {
      return Promise.reject(new Error("Agent Workbench control socket is unavailable"));
    }
    const requestId = crypto.randomUUID();
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        pendingRequests.delete(requestId);
        reject(new Error(`Timed out waiting for Agent Workbench control response: ${type}`));
      }, 10_000);
      pendingRequests.set(requestId, {resolve, reject, timeout});
      const sent = socket.sendText(JSON.stringify({
        type,
        requestId,
        sessionId,
        cwd,
        ...payload,
      }));
      if (!sent) {
        pendingRequests.delete(requestId);
        clearTimeout(timeout);
        reject(new Error("Failed to send Agent Workbench control request"));
      }
    });
  };

  const requireOk = (response: AgentWorkbenchControlCommand): AgentWorkbenchControlCommand => {
    if (response.ok !== true) {
      throw new Error(response.error ?? "Agent Workbench control request failed");
    }
    return response;
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
    requestTaskFolder: async <T = unknown>(operation: string, args: Record<string, unknown> = {}): Promise<T> => {
      const response = requireOk(await sendRequest("taskFolderRequest", {operation, arguments: args}));
      return response.result as T;
    },
    close: () => {
      for (const [requestId, pending] of pendingRequests) {
        pendingRequests.delete(requestId);
        clearTimeout(pending.timeout);
        pending.reject(new Error("Agent Workbench control socket closed"));
      }
      socket.close();
    },
  };
}

type AgentWorkbenchTaskFolderToolDefinition = {
  name: string;
  label: string;
  description: string;
  promptSnippet?: string;
  promptGuidelines?: string[];
  parameters: ReturnType<typeof Type.Object>;
  operation: string;
  arguments?: (params: Record<string, unknown>) => Record<string, unknown>;
  confirm?: (params: Record<string, unknown>, ctx: ExtensionContext) => Promise<boolean>;
  resultText: (result: unknown) => string;
};

export function registerTaskFolderTools(
  pi: ExtensionAPI,
  bridgeProvider: () => AgentWorkbenchControlBridge | undefined,
): void {
  for (const definition of TASK_FOLDER_TOOL_DEFINITIONS) {
    pi.registerTool(defineTool({
      name: definition.name,
      label: definition.label,
      description: definition.description,
      promptSnippet: definition.promptSnippet,
      promptGuidelines: definition.promptGuidelines,
      parameters: definition.parameters,
      async execute(_toolCallId, params, _signal, _onUpdate, ctx) {
        const normalizedParams = params as Record<string, unknown>;
        const bridge = bridgeProvider();
        if (bridge === undefined) {
          throw new Error("Agent Workbench control bridge is unavailable");
        }
        if (definition.confirm !== undefined && !(await definition.confirm(normalizedParams, ctx))) {
          return {
            content: [{type: "text", text: "Cancelled task folder operation."}],
            details: {cancelled: true},
          };
        }
        const result = await bridge.requestTaskFolder(definition.operation, definition.arguments?.(normalizedParams) ?? normalizedParams);
        return {
          content: [{type: "text", text: definition.resultText(result)}],
          details: result,
        };
      },
    }));
  }
}

const TASK_FOLDER_METADATA_DESCRIPTION = "String metadata. Conventional keys are 'issue' and 'review'; custom keys are allowed.";

const TASK_FOLDER_TOOL_DEFINITIONS: AgentWorkbenchTaskFolderToolDefinition[] = [
  {
    name: "agent_workbench_get_current_task_folder",
    label: "Get Current Task Folder",
    description: "Get the Agent Workbench task folder assigned to the current Pi session, if any.",
    promptSnippet: "Inspect the task folder assigned to the current Pi thread",
    promptGuidelines: [
      "Use agent_workbench_get_current_task_folder before updating a task folder when you are unsure whether this Pi thread is already assigned.",
    ],
    parameters: Type.Object({}),
    operation: "getCurrent",
    resultText: (result) => {
      const folder = resultFolder(result);
      return folder === undefined ? "No task folder is assigned to this thread." : `Current task folder: '${folder.name}'.`;
    },
  },
  {
    name: "agent_workbench_list_task_folders",
    label: "List Task Folders",
    description: "List Agent Workbench task folders for the current project.",
    promptSnippet: "List Agent Workbench task folders for the current project",
    promptGuidelines: [
      "Use agent_workbench_list_task_folders when you need a task folder id or need to choose an existing task folder.",
    ],
    parameters: Type.Object({
      includeDone: Type.Optional(Type.Boolean({description: "Whether to include done task folders"})),
    }),
    operation: "listFolders",
    resultText: (result) => `Found ${resultFolders(result).length} task folder(s).`,
  },
  {
    name: "agent_workbench_list_task_folder_threads",
    label: "List Task Folder Threads",
    description: "List threads assigned to an Agent Workbench task folder. Defaults to the current task folder.",
    promptSnippet: "List threads assigned to an Agent Workbench task folder",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
    }),
    operation: "listThreads",
    resultText: (result) => `Found ${resultThreads(result).length} assigned thread(s).`,
  },
  {
    name: "agent_workbench_create_task_folder",
    label: "Create Task Folder",
    description: "Create an Agent Workbench task folder for the current project and assign the current Pi session to it.",
    promptSnippet: "Create an Agent Workbench task folder and assign this Pi thread to it",
    promptGuidelines: [
      "Use agent_workbench_create_task_folder when the user asks to create a task folder or start work in a new task folder.",
      "Use metadata key 'issue' for issue tracker ids and 'review' for review ids; do not use separate issue parameters.",
      "If the current Pi thread already has a task folder, update it with metadata or rename tools instead of creating another folder.",
    ],
    parameters: Type.Object({
      name: Type.String({description: "Task folder name"}),
      metadata: Type.Optional(Type.Record(Type.String(), Type.String({description: TASK_FOLDER_METADATA_DESCRIPTION}))),
    }),
    operation: "createAndAssign",
    resultText: (result) => {
      const folder = resultFolder(result);
      if (folder === undefined) {
        return "Task folder request completed.";
      }
      return resultBoolean(result, "created") === false
        ? `This thread is already assigned to task folder '${folder.name}'. Use metadata or rename tools for updates.`
        : `Created task folder '${folder.name}' and assigned this thread.`;
    },
  },
  {
    name: "agent_workbench_assign_current_thread_to_task_folder",
    label: "Assign Current Thread",
    description: "Assign the current Pi session to an existing Agent Workbench task folder in the current project.",
    promptSnippet: "Assign this Pi thread to an existing task folder",
    parameters: Type.Object({
      folderId: Type.String({description: "Task folder id"}),
    }),
    operation: "assignCurrentThread",
    resultText: mutationResultText("Assigned current thread to task folder."),
  },
  {
    name: "agent_workbench_remove_current_thread_from_task_folder",
    label: "Remove Current Thread",
    description: "Remove the current Pi session from its Agent Workbench task folder.",
    promptSnippet: "Remove this Pi thread from its current task folder",
    parameters: Type.Object({}),
    operation: "unassignCurrentThread",
    resultText: mutationResultText("Removed current thread from task folder."),
  },
  {
    name: "agent_workbench_rename_task_folder",
    label: "Rename Task Folder",
    description: "Rename an Agent Workbench task folder. Defaults to the current task folder.",
    promptSnippet: "Rename an Agent Workbench task folder",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
      name: Type.String({description: "New task folder name"}),
    }),
    operation: "rename",
    resultText: (result) => {
      const folder = resultFolder(result);
      return folder === undefined ? "Task folder rename completed." : `Renamed task folder to '${folder.name}'.`;
    },
  },
  {
    name: "agent_workbench_set_task_folder_metadata",
    label: "Set Task Folder Metadata",
    description: "Set a string metadata key on an Agent Workbench task folder. Defaults to the current task folder.",
    promptSnippet: "Set task folder metadata such as issue or review",
    promptGuidelines: [
      "Use agent_workbench_set_task_folder_metadata to associate an issue id with an existing task folder using key 'issue'.",
      "Use agent_workbench_set_task_folder_metadata to associate a review id with an existing task folder using key 'review'.",
    ],
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
      key: Type.String({description: "Metadata key. Conventional keys are 'issue' and 'review'."}),
      value: Type.String({description: "Metadata value"}),
    }),
    operation: "setMetadata",
    resultText: mutationResultText("Updated task folder metadata."),
  },
  {
    name: "agent_workbench_delete_task_folder_metadata",
    label: "Delete Task Folder Metadata",
    description: "Delete a metadata key from an Agent Workbench task folder. Defaults to the current task folder.",
    promptSnippet: "Delete task folder metadata",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
      key: Type.String({description: "Metadata key to delete"}),
    }),
    operation: "deleteMetadata",
    resultText: mutationResultText("Deleted task folder metadata."),
  },
  {
    name: "agent_workbench_mark_task_folder_done",
    label: "Mark Task Folder Done",
    description: "Mark an Agent Workbench task folder done after archiving assigned threads. Defaults to the current task folder.",
    promptSnippet: "Mark a task folder done and archive assigned threads",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
    }),
    operation: "markDone",
    confirm: (_params, ctx) => confirmTaskFolderOperation(ctx, "Mark Task Folder Done", "Archive assigned threads and mark this task folder done?"),
    resultText: (result) => {
      const archived = resultNumber(result, "archivedCount");
      const requested = resultNumber(result, "requestedCount");
      return `Marked task folder done. Archived ${archived} of ${requested} assigned thread(s).`;
    },
  },
  {
    name: "agent_workbench_delete_task_folder",
    label: "Delete Task Folder",
    description: "Delete an Agent Workbench task folder and remove its assignments. Threads are not archived.",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
    }),
    operation: "delete",
    confirm: (_params, ctx) => confirmTaskFolderOperation(ctx, "Delete Task Folder", "Delete this task folder and remove its thread assignments? Threads will not be archived."),
    resultText: mutationResultText("Deleted task folder."),
  },
];

async function confirmTaskFolderOperation(ctx: ExtensionContext, title: string, message: string): Promise<boolean> {
  if (!ctx.hasUI) {
    throw new Error("Task folder operation requires interactive confirmation");
  }
  return ctx.ui.confirm(title, message);
}

function mutationResultText(changedText: string): (result: unknown) => string {
  return (result) => resultBoolean(result, "changed") ? changedText : "Task folder was already up to date.";
}

function resultFolder(result: unknown): AgentWorkbenchTaskFolder | undefined {
  if (!isRecord(result)) return undefined;
  const folder = result.folder;
  return isRecord(folder) && typeof folder.name === "string" ? folder as AgentWorkbenchTaskFolder : undefined;
}

function resultFolders(result: unknown): AgentWorkbenchTaskFolder[] {
  if (!isRecord(result) || !Array.isArray(result.folders)) return [];
  return result.folders.filter(isRecord) as AgentWorkbenchTaskFolder[];
}

function resultThreads(result: unknown): AgentWorkbenchTaskFolderThread[] {
  if (!isRecord(result) || !Array.isArray(result.threads)) return [];
  return result.threads.filter(isRecord) as AgentWorkbenchTaskFolderThread[];
}

function resultBoolean(result: unknown, key: string): boolean | undefined {
  if (!isRecord(result)) return undefined;
  return typeof result[key] === "boolean" ? result[key] : undefined;
}

function resultNumber(result: unknown, key: string): number {
  if (!isRecord(result)) return 0;
  return typeof result[key] === "number" ? result[key] : 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
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
