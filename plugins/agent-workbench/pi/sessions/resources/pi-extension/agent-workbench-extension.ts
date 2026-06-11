import {watch, type FSWatcher} from "node:fs";
import {readFile} from "node:fs/promises";
import {homedir} from "node:os";
import {basename, dirname, join} from "node:path";
import {streamSimpleOpenAICompletions, type Model} from "@earendil-works/pi-ai";
import {
  AuthStorage,
  Theme,
  type ExtensionAPI,
  type ExtensionContext,
  type ProviderConfig,
  type ProviderModelConfig,
  type ThemeBg,
  type ThemeColor,
} from "@earendil-works/pi-coding-agent";
import {getCapabilities} from "@earendil-works/pi-tui";

const THEME_STATE_ENV = "AGENT_WORKBENCH_PI_THEME_STATE";
const THEME_STATE_FILE = process.env[THEME_STATE_ENV];
const OMLX_PROVIDER_ENV = "AGENT_WORKBENCH_PI_OMLX_PROVIDER";
const OMLX_PROVIDER_METADATA = process.env[OMLX_PROVIDER_ENV];
const STATUS_ENDPOINT_ENV = "AGENT_WORKBENCH_PI_STATUS_ENDPOINT";
const STATUS_TOKEN_ENV = "AGENT_WORKBENCH_PI_STATUS_TOKEN";
const STATUS_ENDPOINT = process.env[STATUS_ENDPOINT_ENV];
const STATUS_TOKEN = process.env[STATUS_TOKEN_ENV];
const DEFAULT_OMLX_CONTEXT_WINDOW = 128000;
const DEFAULT_OMLX_MAX_TOKENS = 16384;
const DONE_STATUS_IDLE_RECHECK_MS = 150;
const SESSION_INFO_LEAF_POLL_MS = 1000;
const THEME_COLOR_KEYS: ThemeColor[] = [
  "accent",
  "border",
  "borderAccent",
  "borderMuted",
  "success",
  "error",
  "warning",
  "muted",
  "dim",
  "text",
  "thinkingText",
  "userMessageText",
  "customMessageText",
  "customMessageLabel",
  "toolTitle",
  "toolOutput",
  "mdHeading",
  "mdLink",
  "mdLinkUrl",
  "mdCode",
  "mdCodeBlock",
  "mdCodeBlockBorder",
  "mdQuote",
  "mdQuoteBorder",
  "mdHr",
  "mdListBullet",
  "toolDiffAdded",
  "toolDiffRemoved",
  "toolDiffContext",
  "syntaxComment",
  "syntaxKeyword",
  "syntaxFunction",
  "syntaxVariable",
  "syntaxString",
  "syntaxNumber",
  "syntaxType",
  "syntaxOperator",
  "syntaxPunctuation",
  "thinkingOff",
  "thinkingMinimal",
  "thinkingLow",
  "thinkingMedium",
  "thinkingHigh",
  "thinkingXhigh",
  "bashMode",
];
const THEME_BG_KEYS: ThemeBg[] = [
  "selectedBg",
  "userMessageBg",
  "customMessageBg",
  "toolPendingBg",
  "toolSuccessBg",
  "toolErrorBg",
];
const FALLBACK_SNAPSHOT: PiThemeSnapshot = {
  formatVersion: 1,
  themeId: "islands-dark",
  themeName: "Islands Dark",
  dark: true,
  fg: {
    accent: "#3574F0",
    border: "#4E5157",
    borderAccent: "#548AF7",
    borderMuted: "#393B40",
    success: "#6AAB73",
    error: "#DB5C5C",
    warning: "#C9A26D",
    muted: "#B0B7C3",
    dim: "#6F737A",
    text: "#DFE1E5",
    thinkingText: "#B0B7C3",
    userMessageText: "#DFE1E5",
    customMessageText: "#DFE1E5",
    customMessageLabel: "#A571E6",
    toolTitle: "#DFE1E5",
    toolOutput: "#B0B7C3",
    mdHeading: "#C9A26D",
    mdLink: "#548AF7",
    mdLinkUrl: "#B0B7C3",
    mdCode: "#2AACB8",
    mdCodeBlock: "#6AAB73",
    mdCodeBlockBorder: "#4E5157",
    mdQuote: "#B0B7C3",
    mdQuoteBorder: "#393B40",
    mdHr: "#4E5157",
    mdListBullet: "#3574F0",
    toolDiffAdded: "#6AAB73",
    toolDiffRemoved: "#DB5C5C",
    toolDiffContext: "#B0B7C3",
    syntaxComment: "#7A7E85",
    syntaxKeyword: "#CF8E6D",
    syntaxFunction: "#56A8F5",
    syntaxVariable: "#DFE1E5",
    syntaxString: "#6AAB73",
    syntaxNumber: "#2AACB8",
    syntaxType: "#B3AE60",
    syntaxOperator: "#B0B7C3",
    syntaxPunctuation: "#B0B7C3",
    thinkingOff: "#393B40",
    thinkingMinimal: "#6F737A",
    thinkingLow: "#548AF7",
    thinkingMedium: "#2AACB8",
    thinkingHigh: "#A571E6",
    thinkingXhigh: "#DB5C5C",
    bashMode: "#6AAB73",
  },
  bg: {
    selectedBg: "#2E436E",
    userMessageBg: "#212326",
    customMessageBg: "#302A3F",
    toolPendingBg: "#25272B",
    toolSuccessBg: "#253527",
    toolErrorBg: "#3D2828",
  },
};
let cachedTheme: { signature: string; theme: Theme } | undefined;

type PiThemeSnapshot = {
  formatVersion: number;
  themeId: string;
  themeName: string;
  dark: boolean;
  fg: Record<ThemeColor, string | number>;
  bg: Record<ThemeBg, string | number>;
};

type AgentWorkbenchOmlxTokenSource = "pi-auth" | "omlx-settings";

type AgentWorkbenchOmlxProvider = {
  formatVersion: 1;
  baseUrl: string;
  modelId: string;
  displayName: string;
  tokenSource: AgentWorkbenchOmlxTokenSource;
  contextWindow?: number;
  maxTokens?: number;
  reasoning: boolean;
  modelType?: string;
};

export default async function agentWorkbenchTheme(pi: ExtensionAPI) {
  await registerSelectedOmlxProvider(pi);

  let themeWatcher: FSWatcher | undefined;
  let sessionInfoPoll: ReturnType<typeof setInterval> | undefined;
  let scheduledApply: ReturnType<typeof setTimeout> | undefined;
  let scheduledDoneStatus: ReturnType<typeof setTimeout> | undefined;
  let lastStatusSignature: string | undefined;
  let lastSessionInfoSignature: string | undefined;
  let lastSessionInfoLeafId: string | null | undefined;

  const updateLastStatusSignature = (signature: string) => {
    lastStatusSignature = signature;
  };

  const updateLastSessionInfoSignature = (signature: string) => {
    lastSessionInfoSignature = signature;
  };

  const clearScheduledDoneStatus = () => {
    if (scheduledDoneStatus !== undefined) {
      clearTimeout(scheduledDoneStatus);
      scheduledDoneStatus = undefined;
    }
  };

  const postProcessingStatus = (ctx: ExtensionContext) => {
    clearScheduledDoneStatus();
    postStatusIfChanged(ctx, "processing", updateLastStatusSignature, lastStatusSignature);
    checkSessionInfoChanged(ctx, updateLastSessionInfoSignature, lastSessionInfoSignature, rememberSessionInfoLeafId);
  };

  const postDoneStatus = (ctx: ExtensionContext) => {
    clearScheduledDoneStatus();
    postStatusIfChanged(ctx, "done", updateLastStatusSignature, lastStatusSignature);
    checkSessionInfoChanged(ctx, updateLastSessionInfoSignature, lastSessionInfoSignature, rememberSessionInfoLeafId);
  };

  const scheduleDoneStatus = (ctx: ExtensionContext) => {
    checkSessionInfoChanged(ctx, updateLastSessionInfoSignature, lastSessionInfoSignature, rememberSessionInfoLeafId);
    clearScheduledDoneStatus();
    scheduledDoneStatus = setTimeout(() => {
      scheduledDoneStatus = undefined;
      if (ctx.isIdle()) {
        postStatusIfChanged(ctx, "done", updateLastStatusSignature, lastStatusSignature);
      }
    }, DONE_STATUS_IDLE_RECHECK_MS);
  };

  const scheduleApply = (ctx: ExtensionContext) => {
    if (scheduledApply !== undefined) {
      clearTimeout(scheduledApply);
    }
    scheduledApply = setTimeout(() => {
      scheduledApply = undefined;
      void applyCurrentTheme(ctx);
    }, 100);
  };

  const ensureSessionInfoPolling = (ctx: ExtensionContext) => {
    if (sessionInfoPoll !== undefined) {
      return;
    }
    sessionInfoPoll = setInterval(() => {
      const leafId = ctx.sessionManager.getLeafId();
      if (leafId !== lastSessionInfoLeafId) {
        lastSessionInfoLeafId = leafId;
        checkSessionInfoChanged(ctx, updateLastSessionInfoSignature, lastSessionInfoSignature, rememberSessionInfoLeafId);
      }
    }, SESSION_INFO_LEAF_POLL_MS);
  };

  const rememberSessionInfoLeafId = (ctx: ExtensionContext) => {
    lastSessionInfoLeafId = ctx.sessionManager.getLeafId();
  };

  pi.on("session_start", async (_event, ctx) => {
    await applyCurrentTheme(ctx);
    clearScheduledDoneStatus();
    postStatusIfChanged(ctx, resolveStartupActivity(ctx), updateLastStatusSignature, lastStatusSignature);
    checkSessionInfoChanged(ctx, updateLastSessionInfoSignature, lastSessionInfoSignature, rememberSessionInfoLeafId);
    ensureSessionInfoPolling(ctx);
    if (themeWatcher === undefined) {
      themeWatcher = startStateWatcher(scheduleApply, ctx);
    }
  });

  (pi.on as AgentWorkbenchSessionInfoChangedOn)("session_info_changed", (event, ctx) => {
    postSessionInfoChangedIfChanged(ctx, event.name, updateLastSessionInfoSignature, lastSessionInfoSignature);
    rememberSessionInfoLeafId(ctx);
  });

  pi.on("agent_start", (_event, ctx) => {
    postProcessingStatus(ctx);
  });

  pi.on("turn_start", (_event, ctx) => {
    postProcessingStatus(ctx);
  });

  pi.on("message_start", (_event, ctx) => {
    postProcessingStatus(ctx);
  });

  pi.on("message_end", (_event, ctx) => {
    scheduleDoneStatus(ctx);
  });

  pi.on("turn_end", (_event, ctx) => {
    scheduleDoneStatus(ctx);
  });

  pi.on("agent_end", (_event, ctx) => {
    postDoneStatus(ctx);
  });

  pi.on("session_shutdown", () => {
    if (scheduledApply !== undefined) {
      clearTimeout(scheduledApply);
      scheduledApply = undefined;
    }
    clearScheduledDoneStatus();
    if (sessionInfoPoll !== undefined) {
      clearInterval(sessionInfoPoll);
      sessionInfoPoll = undefined;
    }
    lastSessionInfoSignature = undefined;
    lastSessionInfoLeafId = undefined;
    themeWatcher?.close();
    themeWatcher = undefined;
  });
}

async function registerSelectedOmlxProvider(pi: ExtensionAPI): Promise<void> {
  try {
    const model = parseOmlxProviderMetadata(OMLX_PROVIDER_METADATA);
    if (model === undefined) {
      return;
    }
    const apiKey = await resolveOmlxApiKey(model);
    if (apiKey === undefined) {
      return;
    }
    pi.registerProvider(model.baseUrl, toOmlxProviderConfig(model, apiKey));
  }
  catch {
    // oMLX registration is opportunistic; theme/status hooks should still load if local model setup changed.
  }
}

function parseOmlxProviderMetadata(value: string | undefined): AgentWorkbenchOmlxProvider | undefined {
  if (value === undefined) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!isOmlxProviderMetadata(parsed)) {
      return undefined;
    }
    return {
      formatVersion: 1,
      baseUrl: normalizeBaseUrl(parsed.baseUrl),
      modelId: parsed.modelId,
      displayName: parsed.displayName,
      tokenSource: parsed.tokenSource,
      contextWindow: optionalNumber(parsed.contextWindow),
      maxTokens: optionalNumber(parsed.maxTokens),
      reasoning: parsed.reasoning === true,
      modelType: optionalString(parsed.modelType),
    };
  }
  catch {
    return undefined;
  }
}

function isOmlxProviderMetadata(value: unknown): value is {
  formatVersion: 1;
  baseUrl: string;
  modelId: string;
  displayName: string;
  tokenSource: AgentWorkbenchOmlxTokenSource;
  contextWindow?: unknown;
  maxTokens?: unknown;
  reasoning?: unknown;
  modelType?: unknown;
} {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const metadata = value as Record<string, unknown>;
  return metadata.formatVersion === 1 &&
    typeof metadata.baseUrl === "string" && metadata.baseUrl.trim() !== "" &&
    typeof metadata.modelId === "string" && metadata.modelId.trim() !== "" &&
    typeof metadata.displayName === "string" && metadata.displayName.trim() !== "" &&
    (metadata.tokenSource === "pi-auth" || metadata.tokenSource === "omlx-settings");
}

async function resolveOmlxApiKey(model: AgentWorkbenchOmlxProvider): Promise<string | undefined> {
  if (model.tokenSource === "pi-auth") {
    return resolvePiAuthApiKey(model.baseUrl) ?? await readOmlxSettingsApiKey(model.baseUrl);
  }
  return await readOmlxSettingsApiKey(model.baseUrl) ?? resolvePiAuthApiKey(model.baseUrl);
}

function resolvePiAuthApiKey(baseUrl: string): string | undefined {
  const credential = AuthStorage.create().get(baseUrl);
  if (credential?.type !== "api_key") {
    return undefined;
  }
  return credential.key ?? "";
}

async function readOmlxSettingsApiKey(expectedBaseUrl: string): Promise<string | undefined> {
  try {
    const text = await readFile(join(homedir(), ".omlx", "settings.json"), "utf8");
    const settings = JSON.parse(text) as unknown;
    if (typeof settings !== "object" || settings === null) {
      return undefined;
    }
    const raw = settings as Record<string, unknown>;
    const server = typeof raw.server === "object" && raw.server !== null ? raw.server as Record<string, unknown> : undefined;
    const host = optionalString(server?.host) ?? "127.0.0.1";
    const port = optionalNumber(server?.port);
    if (normalizeBaseUrl(buildOmlxSettingsBaseUrl(host, port)) !== expectedBaseUrl) {
      return undefined;
    }
    const auth = typeof raw.auth === "object" && raw.auth !== null ? raw.auth as Record<string, unknown> : undefined;
    return optionalString(auth?.api_key) ?? "";
  }
  catch {
    return undefined;
  }
}

function buildOmlxSettingsBaseUrl(host: string, port: number | undefined): string {
  const endpointHost = host.trim() === "0.0.0.0" ? "127.0.0.1" : host.trim() === "::" ? "[::1]" : host.trim();
  const endpoint = endpointHost.startsWith("http://") || endpointHost.startsWith("https://") ? endpointHost : `http://${endpointHost}`;
  if (port === undefined) {
    return endpoint;
  }
  try {
    const url = new URL(endpoint);
    if (url.port !== "") {
      return endpoint;
    }
    url.port = String(port);
    return url.toString();
  }
  catch {
    return `${endpoint}:${port}`;
  }
}

function toOmlxProviderConfig(model: AgentWorkbenchOmlxProvider, apiKey: string): ProviderConfig {
  return {
    baseUrl: `${model.baseUrl}/v1`,
    apiKey,
    api: "openai-completions",
    authHeader: true,
    streamSimple: (providerModel, context, options) =>
      streamSimpleOpenAICompletions(providerModel as Model<"openai-completions">, context, options),
    models: [toOmlxProviderModel(model)],
  };
}

function toOmlxProviderModel(model: AgentWorkbenchOmlxProvider): ProviderModelConfig {
  return {
    id: model.modelId,
    name: model.displayName,
    reasoning: model.reasoning,
    input: model.modelType?.includes("vlm") ? ["text", "image", "audio"] : ["text"],
    cost: {input: 0, output: 0, cacheRead: 0, cacheWrite: 0},
    contextWindow: model.contextWindow ?? DEFAULT_OMLX_CONTEXT_WINDOW,
    maxTokens: model.maxTokens ?? DEFAULT_OMLX_MAX_TOKENS,
  };
}

function normalizeBaseUrl(raw: string): string {
  const trimmed = raw.trim().replace(/\/+$/, "");
  return trimmed.endsWith("/v1") ? trimmed.slice(0, -3).replace(/\/+$/, "") : trimmed;
}

function optionalString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() !== "" ? value : undefined;
}

function optionalNumber(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

type AgentWorkbenchStatusActivity = "ready" | "processing" | "done";

type AgentWorkbenchSessionInfoChangedEvent = {
  type: "session_info_changed";
  name?: string;
};

type AgentWorkbenchSessionInfoChangedOn = (
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

function resolveStartupActivity(ctx: ExtensionContext): AgentWorkbenchStatusActivity {
  return ctx.isIdle() ? "ready" : "processing";
}

function postStatusIfChanged(
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

function postSessionInfoChangedIfChanged(
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

function checkSessionInfoChanged(
  ctx: ExtensionContext,
  updateLastSessionInfoSignature: (signature: string) => void,
  lastSessionInfoSignature: string | undefined,
  rememberSessionInfoLeafId: (ctx: ExtensionContext) => void,
): void {
  const name = ctx.sessionManager.getSessionName();
  if (lastSessionInfoSignature === undefined && name === undefined) {
    rememberSessionInfoSignature(ctx, name, updateLastSessionInfoSignature);
    rememberSessionInfoLeafId(ctx);
    return;
  }
  postSessionInfoChangedIfChanged(ctx, name, updateLastSessionInfoSignature, lastSessionInfoSignature);
  rememberSessionInfoLeafId(ctx);
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

async function applyCurrentTheme(ctx: ExtensionContext): Promise<void> {
  ctx.ui.setTheme(themeFromSnapshot(await readThemeSnapshot(), ctx.ui));
  ctx.ui.setWorkingIndicator({
    frames: [
      ctx.ui.theme.fg("dim", "·"),
      ctx.ui.theme.fg("muted", "•"),
      ctx.ui.theme.fg("accent", "●"),
      ctx.ui.theme.fg("muted", "•"),
    ],
  });
}

function startStateWatcher(
  scheduleApply: (ctx: ExtensionContext) => void,
  ctx: ExtensionContext,
): FSWatcher | undefined {
  if (THEME_STATE_FILE === undefined) {
    return undefined;
  }

  const stateFileName = basename(THEME_STATE_FILE);
  try {
    return watch(dirname(THEME_STATE_FILE), (_eventType, fileName) => {
      if (fileName === null || fileName.toString() === stateFileName) {
        scheduleApply(ctx);
      }
    });
  }
  catch {
    return undefined;
  }
}

async function readThemeSnapshot(): Promise<PiThemeSnapshot> {
  if (THEME_STATE_FILE === undefined) {
    return FALLBACK_SNAPSHOT;
  }

  try {
    return parseThemeSnapshot(await readFile(THEME_STATE_FILE, "utf8"));
  }
  catch {
    return FALLBACK_SNAPSHOT;
  }
}

function parseThemeSnapshot(text: string): PiThemeSnapshot {
  const value = JSON.parse(text) as unknown;
  if (!isSnapshotShape(value)) {
    throw new Error("Invalid Agent Workbench Pi theme snapshot");
  }
  return value;
}

function isSnapshotShape(value: unknown): value is PiThemeSnapshot {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const snapshot = value as Partial<PiThemeSnapshot>;
  if (snapshot.formatVersion !== 1 || typeof snapshot.themeName !== "string" || typeof snapshot.dark !== "boolean") {
    return false;
  }
  return hasThemeValues(snapshot.fg, THEME_COLOR_KEYS) && hasThemeValues(snapshot.bg, THEME_BG_KEYS);
}

function hasThemeValues<T extends string>(values: unknown, keys: T[]): values is Record<T, string | number> {
  if (typeof values !== "object" || values === null) {
    return false;
  }
  const map = values as Partial<Record<T, unknown>>;
  return keys.every((key) => isColorValue(map[key]));
}

function isColorValue(value: unknown): value is string | number {
  return typeof value === "number" || (typeof value === "string" && (/^#[0-9a-fA-F]{6}$/.test(value) || value === ""));
}

function themeFromSnapshot(snapshot: PiThemeSnapshot, ui: ExtensionContext["ui"]): Theme {
  const signature = JSON.stringify(snapshot);
  if (cachedTheme?.signature !== signature) {
    cachedTheme = {
      signature,
      theme: withBuiltinFallback(
        new Theme(snapshot.fg, snapshot.bg, colorMode(), {name: `agent-workbench-${snapshot.themeName}`}),
        ui.getTheme(snapshot.dark ? "dark" : "light"),
      ),
    };
  }
  return cachedTheme.theme;
}

// Pi may add theme tokens that this snapshot format does not provide yet; Theme.fg/bg
// throw on unknown tokens, so delegate those to the built-in theme instead of crashing.
function withBuiltinFallback(theme: Theme, base: Theme | undefined): Theme {
  if (base === undefined) {
    return theme;
  }
  const fg = theme.fg.bind(theme);
  const bg = theme.bg.bind(theme);
  const getFgAnsi = theme.getFgAnsi.bind(theme);
  const getBgAnsi = theme.getBgAnsi.bind(theme);
  theme.fg = (color, text) => {
    try {
      return fg(color, text);
    }
    catch {
      return base.fg(color, text);
    }
  };
  theme.bg = (color, text) => {
    try {
      return bg(color, text);
    }
    catch {
      return base.bg(color, text);
    }
  };
  theme.getFgAnsi = (color) => {
    try {
      return getFgAnsi(color);
    }
    catch {
      return base.getFgAnsi(color);
    }
  };
  theme.getBgAnsi = (color) => {
    try {
      return getBgAnsi(color);
    }
    catch {
      return base.getBgAnsi(color);
    }
  };
  return theme;
}

function colorMode(): "truecolor" | "256color" {
  return getCapabilities().trueColor ? "truecolor" : "256color";
}
