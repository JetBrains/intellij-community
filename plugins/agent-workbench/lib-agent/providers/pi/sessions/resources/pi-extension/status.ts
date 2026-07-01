import {type ExtensionContext} from "@earendil-works/pi-coding-agent";

const STATUS_ENDPOINT_ENV = "AGENT_WORKBENCH_PI_STATUS_ENDPOINT";
const STATUS_TOKEN_ENV = "AGENT_WORKBENCH_PI_STATUS_TOKEN";
const STATUS_ENDPOINT = process.env[STATUS_ENDPOINT_ENV];
const STATUS_TOKEN = process.env[STATUS_TOKEN_ENV];

export type AgentWorkbenchStatusActivity = "ready" | "processing" | "done";

type AgentWorkbenchSessionInfoChangedEvent = {
  type: "session_info_changed";
  name?: string;
};

export type AgentWorkbenchSessionInfoChangedOn = (
  event: "session_info_changed",
  handler: (event: AgentWorkbenchSessionInfoChangedEvent, ctx: ExtensionContext) => void,
) => void;

type AgentWorkbenchStatusPayload = {
  sessionId: string;
  cwd: string;
  activity: AgentWorkbenchStatusActivity;
  updatedAt: number;
};

type AgentWorkbenchSessionInfoPayload = {
  sessionId: string;
  cwd: string;
  event: "session_info_changed";
  name?: string;
  updatedAt: number;
};

export function resolveStartupActivity(ctx: ExtensionContext): AgentWorkbenchStatusActivity {
  return ctx.isIdle() ? "ready" : "processing";
}

export function postStatusIfChanged(
  ctx: ExtensionContext,
  activity: AgentWorkbenchStatusActivity,
  updateLastStatusSignature: (signature: string) => void,
  lastStatusSignature: string | undefined,
): void {
  const sessionId = ctx.sessionManager.getSessionId();
  const cwd = ctx.cwd;
  if (STATUS_ENDPOINT === undefined || STATUS_TOKEN === undefined || sessionId === undefined || cwd === undefined) {
    return;
  }

  const signature = `${sessionId}\u0000${cwd}\u0000${activity}`;
  if (signature === lastStatusSignature) {
    return;
  }
  void postStatus({sessionId, cwd, activity, updatedAt: Date.now()}).then((posted) => {
    if (posted) {
      updateLastStatusSignature(signature);
    }
  });
}

export function postSessionInfoChangedIfChanged(
  ctx: ExtensionContext,
  name: string | undefined,
  updateLastSessionInfoSignature: (signature: string) => void,
  lastSessionInfoSignature: string | undefined,
): void {
  const signature = createSessionInfoSignature(ctx, name);
  if (signature === undefined || signature === lastSessionInfoSignature) {
    return;
  }

  const sessionId = ctx.sessionManager.getSessionId();
  const cwd = ctx.cwd;
  if (STATUS_ENDPOINT === undefined || STATUS_TOKEN === undefined || sessionId === undefined || cwd === undefined) {
    updateLastSessionInfoSignature(signature);
    return;
  }

  void postStatus({sessionId, cwd, event: "session_info_changed", name, updatedAt: Date.now()}).then((posted) => {
    if (posted) {
      updateLastSessionInfoSignature(signature);
    }
  });
}

export function postInitialSessionInfoIfKnown(
  ctx: ExtensionContext,
  updateLastSessionInfoSignature: (signature: string) => void,
  lastSessionInfoSignature: string | undefined,
): void {
  const name = ctx.sessionManager.getSessionName();
  if (lastSessionInfoSignature === undefined && name === undefined) {
    rememberSessionInfoSignature(ctx, name, updateLastSessionInfoSignature);
    return;
  }
  postSessionInfoChangedIfChanged(ctx, name, updateLastSessionInfoSignature, lastSessionInfoSignature);
}

function rememberSessionInfoSignature(
  ctx: ExtensionContext,
  name: string | undefined,
  updateLastSessionInfoSignature: (signature: string) => void,
): void {
  const signature = createSessionInfoSignature(ctx, name);
  if (signature !== undefined) {
    updateLastSessionInfoSignature(signature);
  }
}

function createSessionInfoSignature(ctx: ExtensionContext, name: string | undefined): string | undefined {
  const sessionId = ctx.sessionManager.getSessionId();
  const cwd = ctx.cwd;
  if (sessionId === undefined || cwd === undefined) {
    return undefined;
  }
  return `${sessionId}\u0000${cwd}\u0000${name ?? ""}`;
}

async function postStatus(payload: AgentWorkbenchStatusPayload | AgentWorkbenchSessionInfoPayload): Promise<boolean> {
  try {
    const response = await fetch(STATUS_ENDPOINT!, {
      method: "POST",
      headers: {
        "authorization": `Bearer ${STATUS_TOKEN}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    return response.ok;
  }
  catch {
    return false;
  }
}
