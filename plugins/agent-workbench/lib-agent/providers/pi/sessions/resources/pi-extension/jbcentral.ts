import {execFile} from "node:child_process";
import {readFile} from "node:fs/promises";
import {homedir} from "node:os";
import {join} from "node:path";
import {promisify} from "node:util";
import {
  streamSimpleAnthropic,
  streamSimpleOpenAICodexResponses,
  streamSimpleOpenAIResponses,
  type Model,
} from "@earendil-works/pi-ai";
import {type ExtensionAPI, type ProviderConfig, type ProviderModelConfig} from "@earendil-works/pi-coding-agent";
import {
  type AgentWorkbenchJbCentralAgent,
  type AgentWorkbenchJbCentralLaunchMetadata,
  type AgentWorkbenchJbCentralProvider,
  type AgentWorkbenchJbCentralProxyAccess,
  type AgentWorkbenchJbCentralProxyConfig,
  type AgentWorkbenchJbCentralProxyMetadata,
  type AgentWorkbenchModelCatalog,
  JBCENTRAL_PROVIDER_NAME,
  normalizeBaseUrl,
  optionalNumber,
  optionalPositiveInteger,
  optionalString,
} from "./metadata.ts";

const JBCENTRAL_PROVIDER_ENV = "AGENT_WORKBENCH_PI_JBCENTRAL_PROVIDER";
const JBCENTRAL_PROVIDER_METADATA = process.env[JBCENTRAL_PROVIDER_ENV];
const DEFAULT_JBCENTRAL_CONTEXT_WINDOW = 200000;
const DEFAULT_JBCENTRAL_MAX_TOKENS = 64000;
const JBCENTRAL_API_KEY = "wire-proxy";
const JBCENTRAL_CODEX_FALLBACK_PROVIDER_NAME = "openai-codex";
const JBCENTRAL_CLAUDE_FALLBACK_PROVIDER_NAME = "anthropic";
const JBCENTRAL_CODEX_BASE_PATH = "codex/openai";
const JBCENTRAL_CLAUDE_BASE_PATH = "claude-code/anthropic";
const JBCENTRAL_ADAPTIVE_THINKING_MODEL_MARKERS = [
  "opus-4-6",
  "opus-4.6",
  "opus-4-7",
  "opus-4.7",
  "opus-4-8",
  "opus-4.8",
  "sonnet-4-6",
  "sonnet-4.6",
];
const JBCENTRAL_PROXY_START_ARGS = ["proxy", "start", "--return-key"];
const execFileAsync = promisify(execFile);

export async function registerJbCentralProvider(pi: ExtensionAPI, modelCatalog: AgentWorkbenchModelCatalog | undefined): Promise<void> {
  try {
    // This also runs under `pi --list-models`; registering here makes Central profiles visible in Pi's /model selector.
    const models = collectJbCentralProviderModels(modelCatalog);
    const primaryModel = models[0];
    if (primaryModel !== undefined) {
      const proxyAccess = await resolveJbCentralProxyAccess(primaryModel);
      if (proxyAccess === undefined) {
        return;
      }
      pi.registerProvider(JBCENTRAL_PROVIDER_NAME, toJbCentralProviderConfig(models, proxyAccess));
      return;
    }

    const launchMetadata = parseJbCentralLaunchMetadata(JBCENTRAL_PROVIDER_METADATA);
    if (launchMetadata === undefined) {
      return;
    }
    const proxyAccess = await resolveJbCentralProxyAccess(launchMetadata);
    if (proxyAccess === undefined) {
      return;
    }
    registerJbCentralFallbackProviders(pi, launchMetadata, proxyAccess);
  }
  catch {
    // JBCentral registration is opportunistic; theme/status hooks should still load if the CLI setup changed.
  }
}

function collectJbCentralProviderModels(modelCatalog: AgentWorkbenchModelCatalog | undefined): AgentWorkbenchJbCentralProvider[] {
  const modelsByIdentity = new Map<string, AgentWorkbenchJbCentralProvider>();
  appendJbCentralProviderModel(modelsByIdentity, parseJbCentralProviderMetadata(JBCENTRAL_PROVIDER_METADATA));
  for (const model of modelCatalog?.jbCentral ?? []) {
    appendJbCentralProviderModel(modelsByIdentity, model);
  }
  return [...modelsByIdentity.values()];
}

function appendJbCentralProviderModel(
  modelsByIdentity: Map<string, AgentWorkbenchJbCentralProvider>,
  model: AgentWorkbenchJbCentralProvider | undefined,
): void {
  if (model === undefined) {
    return;
  }
  modelsByIdentity.set(`${model.agent}\u0000${model.modelId}`, model);
}

export function parseJbCentralProviderMetadata(value: string | undefined): AgentWorkbenchJbCentralProvider | undefined {
  if (value === undefined) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!isJbCentralProviderMetadata(parsed)) {
      return undefined;
    }
    return {
      formatVersion: 2,
      provider: JBCENTRAL_PROVIDER_NAME,
      modelId: parsed.modelId,
      displayName: parsed.displayName,
      jbCentralExecutable: parsed.jbCentralExecutable,
      proxyPort: parsed.proxyPort,
      agent: parsed.agent,
      contextWindow: optionalNumber(parsed.contextWindow),
      maxTokens: optionalNumber(parsed.maxTokens),
      reasoning: parsed.reasoning === true,
      supportsImages: parsed.supportsImages === true,
      profileId: optionalString(parsed.profileId),
    };
  }
  catch {
    return undefined;
  }
}

function isJbCentralProviderMetadata(value: unknown): value is {
  formatVersion: 2;
  provider: string;
  modelId: string;
  displayName: string;
  jbCentralExecutable: string;
  proxyPort: number;
  agent: AgentWorkbenchJbCentralAgent;
  contextWindow?: unknown;
  maxTokens?: unknown;
  reasoning?: unknown;
  supportsImages?: unknown;
  profileId?: unknown;
} {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const metadata = value as Record<string, unknown>;
  return metadata.formatVersion === 2 &&
    metadata.provider === JBCENTRAL_PROVIDER_NAME &&
    typeof metadata.modelId === "string" && metadata.modelId.trim() !== "" &&
    typeof metadata.displayName === "string" && metadata.displayName.trim() !== "" &&
    typeof metadata.jbCentralExecutable === "string" && metadata.jbCentralExecutable.trim() !== "" &&
    typeof metadata.proxyPort === "number" && Number.isInteger(metadata.proxyPort) && metadata.proxyPort > 0 &&
    (metadata.agent === "codex" || metadata.agent === "claude-code");
}

function parseJbCentralLaunchMetadata(value: string | undefined): AgentWorkbenchJbCentralLaunchMetadata | undefined {
  if (value === undefined) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!isJbCentralLaunchMetadata(parsed)) {
      return undefined;
    }
    return {
      formatVersion: 2,
      provider: JBCENTRAL_PROVIDER_NAME,
      jbCentralExecutable: parsed.jbCentralExecutable,
      proxyPort: parsed.proxyPort,
      agents: parsed.agents.filter(isJbCentralAgent),
    };
  }
  catch {
    return undefined;
  }
}

function isJbCentralLaunchMetadata(value: unknown): value is {
  formatVersion: 2;
  provider: string;
  jbCentralExecutable: string;
  proxyPort: number;
  agents: unknown[];
} {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const metadata = value as Record<string, unknown>;
  return metadata.formatVersion === 2 &&
    metadata.provider === JBCENTRAL_PROVIDER_NAME &&
    typeof metadata.jbCentralExecutable === "string" && metadata.jbCentralExecutable.trim() !== "" &&
    typeof metadata.proxyPort === "number" && Number.isInteger(metadata.proxyPort) && metadata.proxyPort > 0 &&
    Array.isArray(metadata.agents) && metadata.agents.some(isJbCentralAgent);
}

function isJbCentralAgent(value: unknown): value is AgentWorkbenchJbCentralAgent {
  return value === "codex" || value === "claude-code";
}

async function resolveJbCentralProxyAccess(provider: AgentWorkbenchJbCentralProxyMetadata): Promise<AgentWorkbenchJbCentralProxyAccess | undefined> {
  const proxyConfig = await readJbCentralProxyConfig();
  const proxyPort = proxyConfig?.proxyPort ?? provider.proxyPort;
  try {
    const result = await execFileAsync(provider.jbCentralExecutable, JBCENTRAL_PROXY_START_ARGS, {timeout: 10000});
    const proxySecret = String(result.stdout).trim() || undefined;
    if (proxySecret !== undefined) {
      return {proxyPort, proxySecret};
    }
  }
  catch {
    // Fall back to the running proxy config written by JBCentral CLI.
  }
  const proxySecret = proxyConfig?.proxySecret;
  return proxySecret === undefined ? undefined : {proxyPort, proxySecret};
}

function registerJbCentralFallbackProviders(
  pi: ExtensionAPI,
  launchMetadata: AgentWorkbenchJbCentralLaunchMetadata,
  proxyAccess: AgentWorkbenchJbCentralProxyAccess,
): void {
  // Last-resort catalog fallback: overriding PI built-ins keeps PI's static model rows, which Kotlin recodes to JetBrains Central.
  // Profile-backed Central rows are preferred and suppress this static list before users see it.
  if (launchMetadata.agents.includes("codex")) {
    pi.registerProvider(JBCENTRAL_CODEX_FALLBACK_PROVIDER_NAME, {
      baseUrl: buildJbCentralBaseUrl(proxyAccess, JBCENTRAL_CODEX_BASE_PATH),
      apiKey: JBCENTRAL_API_KEY,
      api: "openai-codex-responses",
    });
  }
  if (launchMetadata.agents.includes("claude-code")) {
    pi.registerProvider(JBCENTRAL_CLAUDE_FALLBACK_PROVIDER_NAME, {
      baseUrl: buildJbCentralBaseUrl(proxyAccess, JBCENTRAL_CLAUDE_BASE_PATH),
      apiKey: JBCENTRAL_API_KEY,
      api: "anthropic-messages",
    });
  }
}

async function readJbCentralProxyConfig(): Promise<AgentWorkbenchJbCentralProxyConfig | undefined> {
  try {
    const parsed = JSON.parse(await readFile(join(homedir(), ".wire", "config.json"), "utf8")) as unknown;
    if (typeof parsed !== "object" || parsed === null) {
      return undefined;
    }
    const config = parsed as Record<string, unknown>;
    return {
      proxyPort: optionalPositiveInteger(config.proxy_port),
      proxySecret: optionalString(config.proxy_secret),
    };
  }
  catch {
    return undefined;
  }
}

function toJbCentralProviderConfig(models: AgentWorkbenchJbCentralProvider[], proxyAccess: AgentWorkbenchJbCentralProxyAccess): ProviderConfig {
  const primaryModel = models[0];
  if (primaryModel === undefined) {
    throw new Error("Cannot register JetBrains Central provider without models");
  }
  return {
    name: JBCENTRAL_PROVIDER_NAME,
    baseUrl: buildJbCentralModelBaseUrl(primaryModel, proxyAccess),
    apiKey: JBCENTRAL_API_KEY,
    api: toJbCentralProviderApi(primaryModel),
    streamSimple: (providerModel, context, options) => {
      if (providerModel.api === "anthropic-messages") {
        return streamSimpleAnthropic(providerModel as Model<"anthropic-messages">, context, options);
      }
      const codexModel = providerModel as Model<"openai-codex-responses">;
      if (isJbCentralCodexModel(codexModel)) {
        return streamSimpleOpenAIResponses(codexModel as Model<"openai-responses">, context, options);
      }
      return streamSimpleOpenAICodexResponses(codexModel, context, options);
    },
    models: models.map((model) => toJbCentralProviderModel(model, proxyAccess)),
  };
}

function toJbCentralProviderModel(model: AgentWorkbenchJbCentralProvider, proxyAccess: AgentWorkbenchJbCentralProxyAccess): ProviderModelConfig {
  return {
    id: model.modelId,
    name: model.displayName,
    api: toJbCentralProviderApi(model),
    baseUrl: buildJbCentralModelBaseUrl(model, proxyAccess),
    reasoning: model.reasoning,
    input: model.supportsImages ? ["text", "image"] : ["text"],
    cost: {input: 0, output: 0, cacheRead: 0, cacheWrite: 0},
    contextWindow: model.contextWindow ?? DEFAULT_JBCENTRAL_CONTEXT_WINDOW,
    maxTokens: model.maxTokens ?? DEFAULT_JBCENTRAL_MAX_TOKENS,
    ...(isJbCentralAdaptiveThinkingModel(model) ? {compat: {forceAdaptiveThinking: true}} : {}),
  };
}

function toJbCentralProviderApi(model: AgentWorkbenchJbCentralProvider): "anthropic-messages" | "openai-codex-responses" {
  return model.agent === "claude-code" ? "anthropic-messages" : "openai-codex-responses";
}

function isJbCentralAdaptiveThinkingModel(model: AgentWorkbenchJbCentralProvider): boolean {
  if (model.agent !== "claude-code") {
    return false;
  }
  const modelId = model.modelId.toLowerCase();
  return JBCENTRAL_ADAPTIVE_THINKING_MODEL_MARKERS.some((marker) => modelId.includes(marker));
}

function buildJbCentralModelBaseUrl(model: AgentWorkbenchJbCentralProvider, proxyAccess: AgentWorkbenchJbCentralProxyAccess): string {
  return buildJbCentralBaseUrl(
    proxyAccess,
    model.agent === "claude-code" ? JBCENTRAL_CLAUDE_BASE_PATH : JBCENTRAL_CODEX_BASE_PATH,
  );
}

function buildJbCentralBaseUrl(
  proxyAccess: AgentWorkbenchJbCentralProxyAccess,
  basePath: string,
): string {
  return `http://127.0.0.1:${proxyAccess.proxyPort}/wire/${proxyAccess.proxySecret}/${basePath}`;
}

function isJbCentralCodexModel(model: Model<"openai-codex-responses">): boolean {
  return model.provider === JBCENTRAL_PROVIDER_NAME && normalizeBaseUrl(model.baseUrl).endsWith(`/${JBCENTRAL_CODEX_BASE_PATH}`);
}
