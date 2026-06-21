export const OMLX_PROVIDER_NAME = "oMLX";
export const JBCENTRAL_PROVIDER_NAME = "JetBrains Central";

export type AgentWorkbenchOmlxTokenSource = "pi-auth" | "omlx-settings";

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

export type AgentWorkbenchJbCentralAgent = "codex" | "claude-code";

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
