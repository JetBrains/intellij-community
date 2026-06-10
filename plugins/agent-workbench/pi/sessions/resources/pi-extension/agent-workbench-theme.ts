import { watch, type FSWatcher } from "node:fs";
import { readFile } from "node:fs/promises";
import { basename, dirname } from "node:path";
import { Theme, type ExtensionAPI, type ExtensionContext } from "@earendil-works/pi-coding-agent";
import { getCapabilities } from "@earendil-works/pi-tui";

type ThemeMode = "dark" | "light";

const THEME_STATE_ENV = "AGENT_WORKBENCH_PI_THEME_STATE";
const THEME_STATE_FILE = process.env[THEME_STATE_ENV];
const THEME_CACHE = new Map<ThemeMode, Theme>();

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
  ctx.ui.setTheme(themeForMode(await readThemeMode()));
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

async function readThemeMode(): Promise<ThemeMode> {
  if (THEME_STATE_FILE === undefined) {
    return "dark";
  }

  try {
    const value = (await readFile(THEME_STATE_FILE, "utf8")).trim();
    return value === "light" ? "light" : "dark";
  }
  catch {
    return "dark";
  }
}

function themeForMode(mode: ThemeMode): Theme {
  let cached = THEME_CACHE.get(mode);
  if (cached === undefined) {
    cached = mode === "dark" ? createDarkTheme() : createLightTheme();
    THEME_CACHE.set(mode, cached);
  }
  return cached;
}

function createDarkTheme(): Theme {
  return new Theme(
    {
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
    {
      selectedBg: "#2E436E",
      userMessageBg: "#2B2D30",
      customMessageBg: "#302A3F",
      toolPendingBg: "#25272B",
      toolSuccessBg: "#253527",
      toolErrorBg: "#3D2828",
    },
    colorMode(),
    { name: "agent-workbench-intellij-dark" },
  );
}

function createLightTheme(): Theme {
  return new Theme(
    {
      accent: "#0E65D7",
      border: "#C9CDD6",
      borderAccent: "#0E65D7",
      borderMuted: "#E1E3E8",
      success: "#208A3C",
      error: "#C7222D",
      warning: "#8F6500",
      muted: "#5F6570",
      dim: "#8C929C",
      text: "#1F2328",
      thinkingText: "#5F6570",
      userMessageText: "#1F2328",
      customMessageText: "#1F2328",
      customMessageLabel: "#6C3AAD",
      toolTitle: "#1F2328",
      toolOutput: "#5F6570",
      mdHeading: "#8F6500",
      mdLink: "#0E65D7",
      mdLinkUrl: "#5F6570",
      mdCode: "#007F8C",
      mdCodeBlock: "#208A3C",
      mdCodeBlockBorder: "#C9CDD6",
      mdQuote: "#5F6570",
      mdQuoteBorder: "#E1E3E8",
      mdHr: "#C9CDD6",
      mdListBullet: "#0E65D7",
      toolDiffAdded: "#208A3C",
      toolDiffRemoved: "#C7222D",
      toolDiffContext: "#5F6570",
      syntaxComment: "#8C8C8C",
      syntaxKeyword: "#0033B3",
      syntaxFunction: "#00627A",
      syntaxVariable: "#1F2328",
      syntaxString: "#067D17",
      syntaxNumber: "#1750EB",
      syntaxType: "#2E7D32",
      syntaxOperator: "#1F2328",
      syntaxPunctuation: "#5F6570",
      thinkingOff: "#E1E3E8",
      thinkingMinimal: "#8C929C",
      thinkingLow: "#0E65D7",
      thinkingMedium: "#007F8C",
      thinkingHigh: "#6C3AAD",
      thinkingXhigh: "#C7222D",
      bashMode: "#208A3C",
    },
    {
      selectedBg: "#D4E5FF",
      userMessageBg: "#F2F3F5",
      customMessageBg: "#F0EBFA",
      toolPendingBg: "#F5F6F8",
      toolSuccessBg: "#EAF6ED",
      toolErrorBg: "#FCECEC",
    },
    colorMode(),
    { name: "agent-workbench-intellij-light" },
  );
}

function colorMode(): "truecolor" | "256color" {
  return getCapabilities().trueColor ? "truecolor" : "256color";
}
