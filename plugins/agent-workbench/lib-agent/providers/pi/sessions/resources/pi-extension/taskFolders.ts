import {Type} from "@earendil-works/pi-ai";
import {defineTool, type ExtensionAPI, type ExtensionContext} from "@earendil-works/pi-coding-agent";
import {type AgentWorkbenchControlBridge} from "./control.ts";

const TASK_FOLDER_REQUEST_TYPE = "taskFolderRequest";

type AgentWorkbenchTaskFolder = {
  path: string;
  id: string;
  name: string;
  status: string;
  metadata?: Record<string, string>;
  createdAt?: number;
  updatedAt?: number;
};

type AgentWorkbenchTaskFolderThread = {
  path: string;
  provider: string;
  threadId: string;
  folderId: string;
  assignedAt?: number;
};

type AgentWorkbenchTaskFolderToolDefinition = {
  name: string;
  label: string;
  description: string;
  promptSnippet?: string;
  promptGuidelines?: string[];
  parameters: ReturnType<typeof Type.Object>;
  operation: string;
  arguments?: (params: Record<string, unknown>) => Record<string, unknown>;
  confirm?: (params: Record<string, unknown>, ctx: ExtensionContext) => Promise<boolean>;
  resultText: (result: unknown) => string;
};

export function registerTaskFolderTools(
  pi: ExtensionAPI,
  bridgeProvider: () => AgentWorkbenchControlBridge | undefined,
): void {
  for (const definition of TASK_FOLDER_TOOL_DEFINITIONS) {
    pi.registerTool(defineTool({
      name: definition.name,
      label: definition.label,
      description: definition.description,
      promptSnippet: definition.promptSnippet,
      promptGuidelines: definition.promptGuidelines,
      parameters: definition.parameters,
      async execute(_toolCallId, params, _signal, _onUpdate, ctx) {
        const normalizedParams = params as Record<string, unknown>;
        const bridge = bridgeProvider();
        if (bridge === undefined) {
          throw new Error("Agent Workbench control bridge is unavailable");
        }
        if (definition.confirm !== undefined && !(await definition.confirm(normalizedParams, ctx))) {
          return {
            content: [{type: "text", text: "Cancelled task folder operation."}],
            details: {cancelled: true},
          };
        }
        const args = definition.arguments?.(normalizedParams) ?? normalizedParams;
        const result = await bridge.request(TASK_FOLDER_REQUEST_TYPE, {operation: definition.operation, arguments: args});
        return {
          content: [{type: "text", text: definition.resultText(result)}],
          details: result,
        };
      },
    }));
  }
}

const TASK_FOLDER_METADATA_DESCRIPTION = "String metadata. Conventional keys are 'issue' and 'review'; custom keys are allowed.";

const TASK_FOLDER_TOOL_DEFINITIONS: AgentWorkbenchTaskFolderToolDefinition[] = [
  {
    name: "agent_workbench_get_current_task_folder",
    label: "Get Current Task Folder",
    description: "Get the Agent Workbench task folder assigned to the current Pi session, if any.",
    promptSnippet: "Inspect the task folder assigned to the current Pi thread",
    promptGuidelines: [
      "Use agent_workbench_get_current_task_folder before updating a task folder when you are unsure whether this Pi thread is already assigned.",
    ],
    parameters: Type.Object({}),
    operation: "getCurrent",
    resultText: (result) => {
      const folder = resultFolder(result);
      return folder === undefined ? "No task folder is assigned to this thread." : `Current task folder: '${folder.name}'.`;
    },
  },
  {
    name: "agent_workbench_list_task_folders",
    label: "List Task Folders",
    description: "List Agent Workbench task folders for the current project.",
    promptSnippet: "List Agent Workbench task folders for the current project",
    promptGuidelines: [
      "Use agent_workbench_list_task_folders when you need a task folder id or need to choose an existing task folder.",
    ],
    parameters: Type.Object({
      includeDone: Type.Optional(Type.Boolean({description: "Whether to include done task folders"})),
    }),
    operation: "listFolders",
    resultText: (result) => `Found ${resultFolders(result).length} task folder(s).`,
  },
  {
    name: "agent_workbench_list_task_folder_threads",
    label: "List Task Folder Threads",
    description: "List threads assigned to an Agent Workbench task folder. Defaults to the current task folder.",
    promptSnippet: "List threads assigned to an Agent Workbench task folder",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
    }),
    operation: "listThreads",
    resultText: (result) => `Found ${resultThreads(result).length} assigned thread(s).`,
  },
  {
    name: "agent_workbench_create_task_folder",
    label: "Create Task Folder",
    description: "Create an Agent Workbench task folder for the current project and assign the current Pi session to it.",
    promptSnippet: "Create an Agent Workbench task folder and assign this Pi thread to it",
    promptGuidelines: [
      "Use agent_workbench_create_task_folder when the user asks to create a task folder or start work in a new task folder.",
      "Use metadata key 'issue' for issue tracker ids and 'review' for review ids; do not use separate issue parameters.",
      "If the current Pi thread already has a task folder, update it with metadata or rename tools instead of creating another folder.",
    ],
    parameters: Type.Object({
      name: Type.String({description: "Task folder name"}),
      metadata: Type.Optional(Type.Record(Type.String(), Type.String({description: TASK_FOLDER_METADATA_DESCRIPTION}))),
    }),
    operation: "createAndAssign",
    resultText: (result) => {
      const folder = resultFolder(result);
      if (folder === undefined) {
        return "Task folder request completed.";
      }
      return resultBoolean(result, "created") === false
        ? `This thread is already assigned to task folder '${folder.name}'. Use metadata or rename tools for updates.`
        : `Created task folder '${folder.name}' and assigned this thread.`;
    },
  },
  {
    name: "agent_workbench_assign_current_thread_to_task_folder",
    label: "Assign Current Thread",
    description: "Assign the current Pi session to an existing Agent Workbench task folder in the current project.",
    promptSnippet: "Assign this Pi thread to an existing task folder",
    parameters: Type.Object({
      folderId: Type.String({description: "Task folder id"}),
    }),
    operation: "assignCurrentThread",
    resultText: mutationResultText("Assigned current thread to task folder."),
  },
  {
    name: "agent_workbench_remove_current_thread_from_task_folder",
    label: "Remove Current Thread",
    description: "Remove the current Pi session from its Agent Workbench task folder.",
    promptSnippet: "Remove this Pi thread from its current task folder",
    parameters: Type.Object({}),
    operation: "unassignCurrentThread",
    resultText: mutationResultText("Removed current thread from task folder."),
  },
  {
    name: "agent_workbench_rename_task_folder",
    label: "Rename Task Folder",
    description: "Rename an Agent Workbench task folder. Defaults to the current task folder.",
    promptSnippet: "Rename an Agent Workbench task folder",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
      name: Type.String({description: "New task folder name"}),
    }),
    operation: "rename",
    resultText: (result) => {
      const folder = resultFolder(result);
      return folder === undefined ? "Task folder rename completed." : `Renamed task folder to '${folder.name}'.`;
    },
  },
  {
    name: "agent_workbench_set_task_folder_metadata",
    label: "Set Task Folder Metadata",
    description: "Set a string metadata key on an Agent Workbench task folder. Defaults to the current task folder.",
    promptSnippet: "Set task folder metadata such as issue or review",
    promptGuidelines: [
      "Use agent_workbench_set_task_folder_metadata to associate an issue id with an existing task folder using key 'issue'.",
      "Use agent_workbench_set_task_folder_metadata to associate a review id with an existing task folder using key 'review'.",
    ],
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
      key: Type.String({description: "Metadata key. Conventional keys are 'issue' and 'review'."}),
      value: Type.String({description: "Metadata value"}),
    }),
    operation: "setMetadata",
    resultText: mutationResultText("Updated task folder metadata."),
  },
  {
    name: "agent_workbench_delete_task_folder_metadata",
    label: "Delete Task Folder Metadata",
    description: "Delete a metadata key from an Agent Workbench task folder. Defaults to the current task folder.",
    promptSnippet: "Delete task folder metadata",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
      key: Type.String({description: "Metadata key to delete"}),
    }),
    operation: "deleteMetadata",
    resultText: mutationResultText("Deleted task folder metadata."),
  },
  {
    name: "agent_workbench_mark_task_folder_done",
    label: "Mark Task Folder Done",
    description: "Mark an Agent Workbench task folder done after archiving assigned threads. Defaults to the current task folder.",
    promptSnippet: "Mark a task folder done and archive assigned threads",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
    }),
    operation: "markDone",
    confirm: (_params, ctx) => confirmTaskFolderOperation(ctx, "Mark Task Folder Done", "Archive assigned threads and mark this task folder done?"),
    resultText: (result) => {
      const archived = resultNumber(result, "archivedCount");
      const requested = resultNumber(result, "requestedCount");
      return `Marked task folder done. Archived ${archived} of ${requested} assigned thread(s).`;
    },
  },
  {
    name: "agent_workbench_delete_task_folder",
    label: "Delete Task Folder",
    description: "Delete an Agent Workbench task folder and remove its assignments. Threads are not archived.",
    parameters: Type.Object({
      folderId: Type.Optional(Type.String({description: "Task folder id; defaults to the current task folder"})),
    }),
    operation: "delete",
    confirm: (_params, ctx) => confirmTaskFolderOperation(ctx, "Delete Task Folder", "Delete this task folder and remove its thread assignments? Threads will not be archived."),
    resultText: mutationResultText("Deleted task folder."),
  },
];

async function confirmTaskFolderOperation(ctx: ExtensionContext, title: string, message: string): Promise<boolean> {
  if (!ctx.hasUI) {
    throw new Error("Task folder operation requires interactive confirmation");
  }
  return ctx.ui.confirm(title, message);
}

function mutationResultText(changedText: string): (result: unknown) => string {
  return (result) => resultBoolean(result, "changed") ? changedText : "Task folder was already up to date.";
}

function resultFolder(result: unknown): AgentWorkbenchTaskFolder | undefined {
  if (!isRecord(result)) return undefined;
  const folder = result.folder;
  return isRecord(folder) && typeof folder.name === "string" ? folder as AgentWorkbenchTaskFolder : undefined;
}

function resultFolders(result: unknown): AgentWorkbenchTaskFolder[] {
  if (!isRecord(result) || !Array.isArray(result.folders)) return [];
  return result.folders.filter(isRecord) as AgentWorkbenchTaskFolder[];
}

function resultThreads(result: unknown): AgentWorkbenchTaskFolderThread[] {
  if (!isRecord(result) || !Array.isArray(result.threads)) return [];
  return result.threads.filter(isRecord) as AgentWorkbenchTaskFolderThread[];
}

function resultBoolean(result: unknown, key: string): boolean | undefined {
  if (!isRecord(result)) return undefined;
  return typeof result[key] === "boolean" ? result[key] : undefined;
}

function resultNumber(result: unknown, key: string): number {
  if (!isRecord(result)) return 0;
  return typeof result[key] === "number" ? result[key] : 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
