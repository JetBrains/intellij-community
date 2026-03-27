---
name: AI Review
description: Requirements for AI-powered code review via ACP agents, multi-session Problems toolwindow integration, prompt popup entry, and review lifecycle management.
targets:
  - ../ai-review/src/**/*.kt
  - ../ai-review/resources/*.xml
  - ../ai-review/resources/messages/*.properties
  - ../ai-review/intellij.agent.workbench.ai.review.iml
  - ../../../../plugins/agent-workbench/ai-review-agents/src/**/*.kt
  - ../../../../plugins/agent-workbench/ai-review-agents/resources/**/*.xml
  - ../../../../plugins/agent-workbench/ai-review-agents/resources/messages/*.properties
---

# AI Review

Status: Draft
Date: 2026-03-27

## Summary
Define the AI Review feature: an ACP-agent-powered code review system integrated into the Problems toolwindow via the Agent Workbench prompt popup, supporting multiple concurrent review sessions with independent result tabs.

The feature is split into two modules:
- **Core** (`intellij.agent.workbench.ai.review`) — community module owning data models, session management, Problems toolwindow UI. Content module of the Agent Workbench plugin.
- **Agents Executor** (`intellij.agent.workbench.ai.review.agents`) — ultimate module providing `AIReviewAcpAgent` as a project-level `@Service` that executes reviews via ACP agents (Claude Code, Codex). Content module of the Agent Workbench plugin; depends on `intellij.ml.llm.agents.acp`.

## Goals
- Support multiple concurrent review sessions, each with an independent Problems toolwindow tab.
- Keep the core module free of ACP and AI Assistant dependencies so it can ship in community builds.
- Provide incremental problem streaming so users see results as the agent works.
- Reuse the Problems toolwindow platform API (`ProblemsViewToolWindowUtils.addTab/removeTab`) for dynamic tab lifecycle.

## Non-goals
- Replacing the existing Self-Review implementation (both coexist independently).
- Defining the ACP protocol or agent registry internals.
- Providing an in-editor annotation layer for review findings.

## Requirements

### Session Management

- `AIReviewSessionManager` must be a project-level `@Service` that manages the lifecycle of concurrent review sessions.

- Each session must receive a unique, monotonically increasing ID with prefix `AIReview_`.

- Creating a session must:
  1. instantiate an `AIReviewSession` with its own `AIReviewViewModel` and `AIReviewProblemsHolder`,
  2. create a dynamic `ProblemsViewPanelProvider` for the session,
  3. add the tab to the Problems toolwindow via `ProblemsViewToolWindowUtils.addTab()`,
  4. select the newly created tab.

- Starting the review is the caller's responsibility: after `createSession()`, the caller obtains `AIReviewAcpAgent` via `project.service<AIReviewAcpAgent>()` and calls `execute()`.

- Removing a session must cancel any running review and remove the tab via `ProblemsViewToolWindowUtils.removeTab()`.

- Cancelling a session must NOT remove its tab; the tab transitions to `Cancelled` state and remains visible with any partial results.

- Tabs must be closeable. Closing must show a confirmation dialog; on confirmation the session is removed via `AIReviewSessionManager.removeSession()`.

### Multi-Tab

- Each review session must create an independent Problems toolwindow tab with its own tree model, problem storage, and state flow.

- Tab display name must include the session number and agent name: "AI Review #N (Agent Name)".

- Multiple tabs must be active and visible simultaneously without interfering with each other.

- Actions (cancel, export, rate, fix) must operate on the session associated with the currently focused tab, resolved via `DataKey<AIReviewSession>` from the panel's `uiDataSnapshot()`.

### ACP Agent

- `AIReviewAcpAgent` must be a project-level `@Service` in the agents executor module (`intellij.agent.workbench.ai.review.agents`).

- It is accessed directly via `project.service<AIReviewAcpAgent>()` by callers that have the agents module on their classpath (the execute action, the VCS agent.workbench bridge).

- There is no extension point indirection; the service-based approach matches the original `VcsAISelfReviewAcpAgent` pattern.

### ACP Agent Lifecycle

- ACP config file path must be `~/.jetbrains/acp-ai-review.json`, always regenerated before each session.

- Agent process must be managed via `EelAcpProcessHandler`.

- Problems must be extracted incrementally via `IncrementalProblemExtractor` during event streaming.

- A safety-net reconciliation parse must run after the stream completes to catch any problems missed by incremental extraction.

- ACP session operations must auto-approve tool calls for READ, SEARCH, and EXECUTE categories; EDIT, DELETE, and MOVE must be rejected.

- ACP agent contributors (Claude Code, Codex) are registered via the `com.intellij.agent.workbench.ai.review.acp.acpAgentContributor` extension point defined in the agents module.

### Prompt Popup Integration

- A palette extension must match when context contains commits (`AgentPromptContextRendererIds.VCS_COMMITS`) or Changes tree selection (`treeKind == "Changes"`).

- The palette extension `getSubmitActionId()` must return `"AIReview.AgentWorkbench.ExecuteAction"`.

- The execute action must:
  1. create a new session via `AIReviewSessionManager.createSession()`,
  2. obtain `AIReviewAcpAgent` via `project.service<AIReviewAcpAgent>()`,
  3. call `agent.execute()` and wire the resulting `Job` to the session's `viewModel.setRunningState()`.

- Agent selection must map from Agent Workbench provider ID: `"claude"` -> Claude Code ACP, `"codex"` -> Codex ACP.

### State Machine (per session)

- Each session's `AIReviewViewModel` must implement the following state transitions:
  - `NotStarted` -> `Running` (with cancel callback)
  - `Running` -> `PartialReviewReceived` (incremental problems)
  - `Running` -> `FullReviewReceived` (all problems)
  - `Running` -> `Error` (with optional partial review)
  - `Running` -> `Cancelled` (with optional partial review)
  - Any -> `FilterApplied` (transient, severity filter toggled)

- `Running` state must expose a `cancel()` callback that transitions to `Cancelled`.

- Partial reviews must preserve all problems received before the error or cancellation.

### Problems Display

- Problems tree structure: File nodes -> Problem nodes -> Description nodes, with a feedback node appended after all file nodes.

- Severity filtering must support toggling visibility of Error, StrongWarning, Warning, and WeakWarning levels.

- Like/Dislike feedback must be available per session.

- Export action must write all session problems to a markdown file.

- Duration must be displayed on the root node during `Running` state, updated every second.

- Problem descriptions containing markdown must be rendered as HTML in the tree cell renderer.

## User Experience
- User opens Agent Workbench prompt popup (`Cmd+\` / `Ctrl+\` or `Alt+Cmd+\` / `Alt+Ctrl+\`).
- "AI Review" tab appears when commits or local changes context is detected.
- User optionally customizes prompt and selects provider (Claude/Codex).
- Submit creates a new "AI Review #N" tab in the Problems toolwindow.
- Problems stream in as the agent analyzes code.
- User can cancel, export, or rate the review.
- Multiple reviews can run simultaneously in separate tabs.
- Tabs are closeable with a confirmation dialog.

## Error Handling
- ACP process failures transition the session to `Error` state with the error message displayed in the tab.
- Partial results are preserved and displayed even on error.

## Testing / Local Run
- `./bazel.cmd build @community//plugins/agent-workbench/ai-review:agent-workbench-ai-review`
- `./bazel.cmd build //plugins/agent-workbench/ai-review-agents:agent-workbench-ai-review-agents`
- `./bazel.cmd build //plugins/agent-workbench/ai-review-space:agent-workbench-ai-review-space`

## References
- `spec/actions/global-prompt-entry.spec.md` — prompt popup behavior
- `spec/prompt-context/prompt-context-vcs.spec.md` — VCS context collection
