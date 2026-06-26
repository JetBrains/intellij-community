export const OMLX_PROVIDER_NAME = "oMLX";
export const JBCENTRAL_PROVIDER_NAME = "JetBrains Central";

export type AgentWorkbenchOmlxTokenSource = "pi-auth" | "omlx-settings";
export type AgentWorkbenchThinkingLevel = "off" | "minimal" | "low" | "medium" | "high" | "xhigh";
export type AgentWorkbenchThinkingLevelMap = Partial<Record<AgentWorkbenchThinkingLevel, string | null>>;

export type AgentWorkbenchOmlxProvider = {
  formatVersion: 1;
  provider: string;
  baseUrl: string;
  modelId: string;
  displayName: string;
  tokenSource: AgentWorkbenchOmlxTokenSource;
  contextWindow?: number;
  maxTokens?: number;
  reasoning: boolean;
  modelType?: string;
};

export type AgentWorkbenchJbCentralAgent = "codex" | "claude-code" | "gemini-cli";

export type AgentWorkbenchJbCentralProvider = {
  formatVersion: 2;
  provider: typeof JBCENTRAL_PROVIDER_NAME;
  modelId: string;
  displayName: string;
  jbCentralExecutable: string;
  proxyPort: number;
  agent: AgentWorkbenchJbCentralAgent;
  contextWindow?: number;
  maxTokens?: number;
  reasoning: boolean;
  thinkingLevelMap?: AgentWorkbenchThinkingLevelMap;
  supportsImages: boolean;
  profileId?: string;
};

export type AgentWorkbenchJbCentralLaunchMetadata = AgentWorkbenchJbCentralProxyMetadata & {
  formatVersion: 2;
  provider: typeof JBCENTRAL_PROVIDER_NAME;
  agents: AgentWorkbenchJbCentralAgent[];
};

export type AgentWorkbenchJbCentralProxyMetadata = {
  jbCentralExecutable: string;
  proxyPort: number;
};

export type AgentWorkbenchJbCentralProxyConfig = {
  proxyPort?: number;
  proxySecret?: string;
};

export type AgentWorkbenchJbCentralProxyAccess = {
  proxyPort: number;
  proxySecret: string;
};

export type AgentWorkbenchModelCatalog = {
  formatVersion: 1;
  omlx: AgentWorkbenchOmlxProvider[];
  jbCentral: AgentWorkbenchJbCentralProvider[];
};

export function normalizeBaseUrl(raw: string): string {
  const trimmed = raw.trim().replace(/\/+$/, "");
  return trimmed.endsWith("/v1") ? trimmed.slice(0, -3).replace(/\/+$/, "") : trimmed;
}

export function optionalString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() !== "" ? value : undefined;
}

export function optionalNumber(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

export function optionalPositiveInteger(value: unknown): number | undefined {
  const parsed = optionalNumber(value);
  return parsed !== undefined && Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

const THINKING_LEVELS: readonly AgentWorkbenchThinkingLevel[] = ["off", "minimal", "low", "medium", "high", "xhigh"];
const THINKING_LEVEL_SET: ReadonlySet<string> = new Set(THINKING_LEVELS);

export function optionalThinkingLevelMap(value: unknown): AgentWorkbenchThinkingLevelMap | undefined {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return undefined;
  }
  const result: AgentWorkbenchThinkingLevelMap = {};
  for (const [level, rawMapping] of Object.entries(value)) {
    if (!isThinkingLevel(level)) {
      return undefined;
    }
    if (rawMapping === null) {
      result[level] = null;
    }
    else if (typeof rawMapping === "string" && rawMapping.trim() !== "") {
      result[level] = rawMapping;
    }
    else {
      return undefined;
    }
  }
  return Object.keys(result).length === 0 ? undefined : result;
}

function isThinkingLevel(value: string): value is AgentWorkbenchThinkingLevel {
  return THINKING_LEVEL_SET.has(value);
}
