import {type ExtensionAPI, type ExtensionContext} from "@earendil-works/pi-coding-agent";
import {startControlBridge} from "./control.ts";
import {registerJbCentralProvider} from "./jbcentral.ts";
import {parseModelCatalogMetadata} from "./modelCatalog.ts";
import {registerOmlxProviders} from "./omlx.ts";
import {
  type AgentWorkbenchSessionInfoChangedOn,
  postInitialSessionInfoIfKnown,
  postSessionInfoChangedIfChanged,
  postStatusIfChanged,
  resolveStartupActivity,
} from "./status.ts";
import {subscribeShiftEnterTerminalInput} from "./terminalInput.ts";
import {registerTaskFolderTools} from "./taskFolders.ts";
import {applyCurrentTheme, startStateWatcher} from "./theme.ts";

const MODEL_CATALOG_ENV = "AGENT_WORKBENCH_PI_MODEL_CATALOG";
const MODEL_CATALOG_METADATA = process.env[MODEL_CATALOG_ENV];
const DONE_STATUS_IDLE_RECHECK_MS = 150;

export default async function agentWorkbenchTheme(pi: ExtensionAPI) {
  const modelCatalog = parseModelCatalogMetadata(MODEL_CATALOG_METADATA);
  await registerOmlxProviders(pi, modelCatalog);
  await registerJbCentralProvider(pi, modelCatalog);

  let themeWatcher: ReturnType<typeof startStateWatcher> | undefined;
  let scheduledApply: ReturnType<typeof setTimeout> | undefined;
  let scheduledDoneStatus: ReturnType<typeof setTimeout> | undefined;
  let terminalInputUnsubscribe: (() => void) | undefined;
  let controlBridge: ReturnType<typeof startControlBridge> | undefined;
  let lastStatusSignature: string | undefined;
  let lastSessionInfoSignature: string | undefined;

  registerTaskFolderTools(pi, () => controlBridge);

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
  };

  const postDoneStatus = (ctx: ExtensionContext) => {
    clearScheduledDoneStatus();
    postStatusIfChanged(ctx, "done", updateLastStatusSignature, lastStatusSignature);
  };

  const scheduleDoneStatus = (ctx: ExtensionContext) => {
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

  const ensureTerminalInputMapping = (ctx: ExtensionContext) => {
    if (terminalInputUnsubscribe !== undefined) {
      return;
    }
    terminalInputUnsubscribe = subscribeShiftEnterTerminalInput(ctx);
  };

  pi.on("session_start", async (_event, ctx) => {
    ensureTerminalInputMapping(ctx);
    if (controlBridge === undefined) {
      controlBridge = startControlBridge(ctx);
    }
    else {
      controlBridge.setContext(ctx);
    }
    await applyCurrentTheme(ctx);
    clearScheduledDoneStatus();
    postStatusIfChanged(ctx, resolveStartupActivity(ctx), updateLastStatusSignature, lastStatusSignature);
    postInitialSessionInfoIfKnown(ctx, updateLastSessionInfoSignature, lastSessionInfoSignature);
    if (themeWatcher === undefined) {
      themeWatcher = startStateWatcher(scheduleApply, ctx);
    }
  });

  (pi.on as AgentWorkbenchSessionInfoChangedOn)("session_info_changed", (event, ctx) => {
    postSessionInfoChangedIfChanged(ctx, event.name, updateLastSessionInfoSignature, lastSessionInfoSignature);
    controlBridge?.setContext(ctx);
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
    terminalInputUnsubscribe?.();
    terminalInputUnsubscribe = undefined;
    if (scheduledApply !== undefined) {
      clearTimeout(scheduledApply);
      scheduledApply = undefined;
    }
    clearScheduledDoneStatus();
    lastSessionInfoSignature = undefined;
    controlBridge?.close();
    controlBridge = undefined;
    themeWatcher?.close();
    themeWatcher = undefined;
  });
}
