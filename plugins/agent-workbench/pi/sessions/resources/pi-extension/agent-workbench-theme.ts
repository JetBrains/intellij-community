import {watch, type FSWatcher} from "node:fs";
import {readFile} from "node:fs/promises";
import {basename, dirname} from "node:path";
import {Theme, type ExtensionAPI, type ExtensionContext, type ThemeBg, type ThemeColor} from "@earendil-works/pi-coding-agent";
import {getCapabilities} from "@earendil-works/pi-tui";

const THEME_STATE_ENV = "AGENT_WORKBENCH_PI_THEME_STATE";
const THEME_STATE_FILE = process.env[THEME_STATE_ENV];
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

export default function agentWorkbenchTheme(pi: ExtensionAPI) {
  let watcher: FSWatcher | undefined;
  let scheduledApply: ReturnType<typeof setTimeout> | undefined;

  const scheduleApply = (ctx: ExtensionContext) => {
    if (scheduledApply !== undefined) {
      clearTimeout(scheduledApply);
    }
    scheduledApply = setTimeout(() => {
      scheduledApply = undefined;
      void applyCurrentTheme(ctx);
    }, 100);
  };

  pi.on("session_start", async (_event, ctx) => {
    await applyCurrentTheme(ctx);
    if (watcher === undefined) {
      watcher = startStateWatcher(scheduleApply, ctx);
    }
  });

  pi.on("session_shutdown", () => {
    if (scheduledApply !== undefined) {
      clearTimeout(scheduledApply);
      scheduledApply = undefined;
    }
    watcher?.close();
    watcher = undefined;
  });
}

async function applyCurrentTheme(ctx: ExtensionContext): Promise<void> {
  ctx.ui.setTheme(themeFromSnapshot(await readThemeSnapshot()));
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

function themeFromSnapshot(snapshot: PiThemeSnapshot): Theme {
  const signature = JSON.stringify(snapshot);
  if (cachedTheme?.signature !== signature) {
    cachedTheme = {
      signature,
      theme: new Theme(snapshot.fg, snapshot.bg, colorMode(), {name: `agent-workbench-${snapshot.themeName}`}),
    };
  }
  return cachedTheme.theme;
}

function colorMode(): "truecolor" | "256color" {
  return getCapabilities().trueColor ? "truecolor" : "256color";
}
