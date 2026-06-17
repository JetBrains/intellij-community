import {parseJbCentralProviderMetadata} from "./jbcentral.ts";
import {type AgentWorkbenchModelCatalog} from "./metadata.ts";
import {parseOmlxProviderMetadata} from "./omlx.ts";

export function parseModelCatalogMetadata(value: string | undefined): AgentWorkbenchModelCatalog | undefined {
  if (value === undefined) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!isModelCatalogMetadata(parsed)) {
      return undefined;
    }
    return {
      formatVersion: 1,
      omlx: parsed.omlx.map(parseOmlxProviderMetadata).filter(isDefined),
      jbCentral: parsed.jbCentral.map(parseJbCentralProviderMetadata).filter(isDefined),
    };
  }
  catch {
    return undefined;
  }
}

function isModelCatalogMetadata(value: unknown): value is { formatVersion: 1; omlx: string[]; jbCentral: string[] } {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const metadata = value as Record<string, unknown>;
  return metadata.formatVersion === 1 && Array.isArray(metadata.omlx) && Array.isArray(metadata.jbCentral) &&
    metadata.omlx.every((entry) => typeof entry === "string") &&
    metadata.jbCentral.every((entry) => typeof entry === "string");
}

function isDefined<T>(value: T | undefined): value is T {
  return value !== undefined;
}
