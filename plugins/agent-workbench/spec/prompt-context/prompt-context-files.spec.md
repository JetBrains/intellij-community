---
name: "Prompt Context: Manual Files"
description: Requirements for the manual files context source, picker scoping, and aggregated `paths` payload output.
targets:
  - ../../prompt/src/context/AgentPromptProjectPathsManualContextSource.kt
  - ../../prompt/src/context/AgentPromptProjectPathsChooserPopup.kt
  - ../../prompt/resources/intellij.agent.workbench.prompt.xml
  - ../../prompt/resources/messages/AgentPromptBundle.properties
  - ../../prompt/testSrc/context/AgentPromptProjectPathsManualContextSourceTest.kt
  - ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt
---

# Prompt Context: Manual Files

Status: Draft
Date: 2026-03-11

## Summary
Define the manual `Files…` prompt context source: registration, project-scoped chooser behavior, selection normalization, aggregated `paths` payload output, and chip/envelope behavior reuse.

## Goals
- Let users add explicit project paths from the global prompt popup without leaving the current flow.
- Keep manual file/folder context path-first and compact.
- Reuse the existing `paths` renderer and payload contract.

## Non-goals
- Defining file-content or directory-content attachment behavior.
- Defining external-file or OS file-chooser behavior.
- Defining multiple independent manual context items for one source.

## Requirements
- Manual files source registration contract:
  - registered through `com.intellij.agent.workbench.promptManualContextSource`,
  - `sourceId = manual.project.paths`,
  - `order = 10`,
  - display name is `Files…`.

- Manual files availability contract:
  - the source uses the resolved `sourceProject`,
  - no additional VCS-style availability gate is required.

- Picker sourcing contract:
  - prefer the resolved `workingProjectPath` subtree when it is under project content,
  - otherwise fall back to all project content roots,
  - always include visible `Scratches and Consoles` roots, such as `Scratches` and `Extensions`, alongside the project-scoped roots,
  - open a lightweight non-modal popup with a scoped project tree that allows selecting files and folders,
  - support multi-select and speed search in the tree,
  - provide a search-by-name tab (using `GotoFileModel`) scoped to the same roots for fast filename lookup,
  - Enter confirms from whichever tab is active (project tree or search-by-name) and adds the currently chosen paths to the existing manual files selection,
  - keep current manual selection preselected when reopening the popup,
  - removing paths happens from the context row chips, not by deselecting inside the chooser,
  - avoid preloading every file and folder in the scoped roots before showing the chooser.
  [@test] ../../prompt/testSrc/context/AgentPromptProjectPathsManualContextSourceTest.kt

- Manual files output contract:
  - produces one context item with `rendererId = paths`,
  - `title = Files`,
  - `itemId = manual.project.paths`,
  - `source = manualPaths`,
  - body lines contain included `file: <path>` or `dir: <path>` entries only.
  [@test] ../../prompt/testSrc/context/AgentPromptProjectPathsManualContextSourceTest.kt

- Manual files payload contract:
  - `entries[]` with `{ kind, path }`,
  - `selectedCount`,
  - `includedCount`,
  - `directoryCount`,
  - `fileCount`.
  [@test] ../../prompt/testSrc/context/AgentPromptProjectPathsManualContextSourceTest.kt

- Manual files normalization and source-limit contract:
  - trim path values,
  - remove empty paths,
  - deduplicate by path,
  - preserve first-seen file/directory metadata,
  - include at most 20 entries in the context item,
  - set truncation reason to `SOURCE_LIMIT` when capped.
  [@test] ../../prompt/testSrc/context/AgentPromptProjectPathsManualContextSourceTest.kt

- Renderer contract:
  - chip and envelope rendering reuse the shared `paths` renderer behavior without a new renderer id.
  [@test] ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt

## User Experience
- Manual path picking should feel like an extension of `Add Context`, not a separate workflow.
- Users should be able to re-open the chooser and confirm or refine prior selections.
- The chooser should preserve additive selection across tab switches and search refinements.
- Attached file and folder selections should render as one removable chip per path.
- Attached path chips should stay compact and path-first.

## Data & Backend
- Manual files reuse the shared `AgentPromptContextItem` and `paths` payload schema already used by project-view context.
- Chooser state is transient; persisted draft state still excludes manual context items.

## Error Handling
- Empty or unavailable project content degrades to popup error feedback.
- Invalid or blank payload paths are ignored during normalization and extraction.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.context.AgentPromptProjectPathsManualContextSourceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptContextEntryPathRenderingTest'`

## References
- `prompt-context-contracts.spec.md`
- `prompt-context-project-view.spec.md`
- `../actions/global-prompt-entry.spec.md`
