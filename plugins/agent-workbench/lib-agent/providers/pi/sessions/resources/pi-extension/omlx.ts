import {readFile} from "node:fs/promises";
import {homedir} from "node:os";
import {join} from "node:path";
import {streamSimpleOpenAICompletions, type Model} from "@earendil-works/pi-ai";
import {AuthStorage, type ExtensionAPI, type ProviderConfig, type ProviderModelConfig} from "@earendil-works/pi-coding-agent";
import {
  type AgentWorkbenchModelCatalog,
  type AgentWorkbenchOmlxProvider,
  type AgentWorkbenchOmlxTokenSource,
  normalizeBaseUrl,
  OMLX_PROVIDER_NAME,
  optionalNumber,
  optionalString,
} from "./metadata.ts";

const OMLX_PROVIDER_ENV = "AGENT_WORKBENCH_PI_OMLX_PROVIDER";
const OMLX_PROVIDER_METADATA = process.env[OMLX_PROVIDER_ENV];
const DEFAULT_OMLX_CONTEXT_WINDOW = 128000;
const DEFAULT_OMLX_MAX_TOKENS = 16384;

export async function registerOmlxProviders(pi: ExtensionAPI, modelCatalog: AgentWorkbenchModelCatalog | undefined): Promise<void> {
  try {
    const modelsByBaseUrl = new Map<string, AgentWorkbenchOmlxProvider[]>();
    appendOmlxProviderModel(modelsByBaseUrl, parseOmlxProviderMetadata(OMLX_PROVIDER_METADATA));
    for (const model of modelCatalog?.omlx ?? []) {
      appendOmlxProviderModel(modelsByBaseUrl, model);
    }
    if (modelsByBaseUrl.size === 0) {
      return;
    }
    for (const models of modelsByBaseUrl.values()) {
      const primaryModel = models[0];
      if (primaryModel === undefined) {
        continue;
      }
      const apiKey = await resolveOmlxApiKey(primaryModel);
      if (apiKey === undefined) {
        continue;
      }
      pi.registerProvider(primaryModel.provider, toOmlxProviderConfig(models, apiKey));
    }
  }
  catch {
    // oMLX registration is opportunistic; theme/status hooks should still load if local model setup changed.
  }
}

function appendOmlxProviderModel(
  modelsByBaseUrl: Map<string, AgentWorkbenchOmlxProvider[]>,
  model: AgentWorkbenchOmlxProvider | undefined,
): void {
  if (model === undefined) {
    return;
  }
  const models = modelsByBaseUrl.get(model.baseUrl) ?? [];
  if (!models.some((existingModel) => existingModel.modelId === model.modelId)) {
    models.push(model);
  }
  modelsByBaseUrl.set(model.baseUrl, models);
}

export function parseOmlxProviderMetadata(value: string | undefined): AgentWorkbenchOmlxProvider | undefined {
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
      provider: optionalString(parsed.provider) ?? OMLX_PROVIDER_NAME,
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
  provider?: unknown;
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
    (metadata.provider === undefined || typeof metadata.provider === "string") &&
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

function toOmlxProviderConfig(models: AgentWorkbenchOmlxProvider[], apiKey: string): ProviderConfig {
  const primaryModel = models[0];
  if (primaryModel === undefined) {
    throw new Error("Cannot register oMLX provider without models");
  }
  return {
    name: OMLX_PROVIDER_NAME,
    baseUrl: `${primaryModel.baseUrl}/v1`,
    apiKey,
    api: "openai-completions",
    authHeader: true,
    streamSimple: (providerModel, context, options) =>
      streamSimpleOpenAICompletions(providerModel as Model<"openai-completions">, context, options),
    models: models.map(toOmlxProviderModel),
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
