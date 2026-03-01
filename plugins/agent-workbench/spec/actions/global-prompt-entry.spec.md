# Global Prompt Entry

## Scope

Defines behavior for the global prompt entry opened with `Cmd+\\` (macOS) or `Ctrl+\\` (Windows/Linux).

The entrypoint is available from any IDE context and routes a composed prompt to a thread target.

## Routing Model

- `PromptTargetMode.NEW_THREAD` - create a new thread and send the composed prompt as the initial message.
- `PromptTargetMode.EXISTING_THREAD` - route the composed prompt to an existing thread.

Current implementation status:

- Implemented: `NEW_THREAD`.
- Implemented: `EXISTING_THREAD` selector, search, and routing.
- Existing-thread picker data source implemented through `sessions-core` prompt launcher bridge backed by app-level `AgentSessionsService` state.

## Contract

1. Action is available from any IDE focus context when a project is open.
2. Popup opens centered in the current IDE window.
3. Popup collects default context from invocation:
   - context is resolved via `com.intellij.agent.workbench.promptContextContributor` EP,
   - contributors consume invocation metadata derived from `AnActionEvent`,
   - first non-empty contributor result in `INVOCATION` phase wins,
   - if `INVOCATION` phase is empty, first non-empty contributor result in `FALLBACK` phase wins.
4. Invocation-specific default context policy:
   - editor invocation yields snippet/file/symbol context,
   - project view invocation yields selected file/directory path context,
   - editor and project-view context are not merged in one launch.
5. Default context does not include a standalone project context entry.
6. If no invocation contributor yields context, fallback uses selected editor snapshot in the project.
7. Default context is captured on popup open; the popup does not provide manual context refresh controls.
8. Popup does not provide manual custom-context input; users can include additional details directly in prompt text.
9. Sending always requires explicit confirmation by user action (keyboard submit or button click, depending on UI variant).
10. Prompt launch routes through `sessions-core` prompt launcher bridge.
11. Context payload uses a 12k soft cap: when exceeded, user explicitly chooses to send full context, auto-trim, or cancel.
12. Context chips that preview filesystem paths render short paths: project-relative when under the current project root, otherwise user-home-relative (for example `~/...`) when under user home.
13. For newly created chat tabs, initial prompt delivery prefers provider startup-command injection (CLI args) when the provider supports it and command-size guard allows it; otherwise fallback is sending the composed prompt exactly once after terminal initialization.
14. UI includes target-mode switch (`NEW_TASK` / `EXISTING_TASK`) and existing-thread picker; `EXISTING_TASK` submit requires an explicit thread selection.
15. Existing-thread picker must consume one shared source of thread data (app-level `AgentSessionsService.state`) and must not call provider session sources directly from prompt UI.
16. Existing-thread picker refresh policy:
   - if current project path is missing from sessions state, trigger one-time catalog/bootstrap refresh,
   - if path is already loaded, trigger provider-scoped background refresh for selected provider + path,
   - refresh must stay background/non-blocking for popup interaction.
17. Snippet language metadata contract for prompt context envelope:
   - editor snippet context stores language in `ctx.language` metadata only,
   - language source is PSI language id,
   - if snippet language is missing or invalid, snippet descriptor omits `lang=...` and snippet code fence is unlabeled (plain triple backticks),
   - legacy metadata key `language` is not supported.

## Follow-up TODOs

- Define queued-send UX and semantics (`Tab` for queue in Codex flow) together with backend implementation.
- Define whether queued-send hint should be shown once and persisted via `PropertiesComponent`.
- Consider explicit runtime prioritization for `AgentWorkbenchPrompt.OpenGlobalPalette` on `Cmd/Ctrl+\\` when multiple enabled actions share the shortcut (for example `AIAssistant.Editor.AskAiAssistantInEditor`, `CodeCompletion`, or `StepOut`).
  Current implementation relies on known keymap duplicate declarations and does not enforce a runtime winner.
