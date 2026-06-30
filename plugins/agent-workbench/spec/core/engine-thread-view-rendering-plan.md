---
name: Engine Thread View Rendering Plan
description: Implementation plan for the minimal readable Engine/ACP thread view renderer: autoscroll, basic transcript rows, ACP raw-output normalization, and verification.
targets:
  - ../../engine/src/ui/AgentAcpThreadScreen.kt
  - ../../engine/resources/messages/EngineBundle.properties
  - ../../../../../plugins/ij-air/acp/src/AcpThreadEventMapper.kt
  - ../../../../../plugins/ij-air/acp/testSrc/AcpThreadEventMapperTest.kt
---

# Engine Thread View Rendering Plan

Status: Draft
Date: 2026-06-26

## Summary

The current Engine ACP screen proves the event-sourced structured-thread path, but its transcript rendering is still MVP-level:
it rebuilds the full panel on every projection update, does not keep the viewport at the live tail during streaming, and renders
many entries as raw text. Tool output from ACP can also leak protocol JSON into the Engine transcript (for example
`{"content":"..."}`), which should be normalized before UI rendering.

This plan defines the smallest useful thread view renderer pass: keep the UI simple Swing, make the transcript readable, add predictable
autoscroll, and normalize ACP raw output at the adapter boundary. It intentionally does not introduce virtualization, rich markdown,
inline diff actions, or approval controls beyond basic display.

## Goals

- Keep the transcript scrolled to the bottom while the user is reading the live tail.
- Preserve the user's manual scroll position when they scroll up to inspect earlier output.
- Render the basic Engine transcript entry types in recognizable rows: messages, tools, commands, plans, diffs, and context entries.
- Avoid huge unbounded output blocks in the thread view by showing UI-only previews for tool, command, and diff bodies.
- Normalize common ACP `rawOutput` shapes into text before persisting Engine events, so the UI does not display protocol JSON blobs.
- Keep persistence and projection semantics unchanged: the event log remains the durable source of truth, and UI truncation never edits stored data.

## Non-goals

- No virtualized transcript list in this pass.
- No markdown renderer, syntax highlighting, or rich diff viewer.
- No interactive tool expansion/collapse UI beyond static previews.
- No approval decision buttons; approvals are displayed as status only.
- No changes to ACP restore/load semantics or Engine persistence.

## Architectural Boundaries

- `AgentAcpThreadScreen` renders only `ThreadProjection`; it must not inspect ACP protocol classes or runtime-specific JSON.
- `AcpThreadEventMapper` owns ACP protocol normalization. If ACP sends text inside raw JSON, the mapper extracts plain text before creating Engine events.
- `ThreadProjection` stays runtime-agnostic. Do not add UI-only fields to projection just to support presentation.
- Large-output preview limits live in the UI layer. Full output remains available in `ThreadToolCall.output`, `ThreadCommand.output`, or diff text fields.
- User-visible labels and truncation messages live in `EngineBundle.properties`.

## Phase 1: Autoscroll Contract

### Current Problem

`AgentAcpThreadScreen` creates the transcript `JBScrollPane` inline and only holds `transcriptPanel`. On each projection update it
removes and rebuilds all child components. There is no viewport policy, so streaming output can leave the user away from the live
tail or make future fixes hard to reason about.

### Implementation

1. Make the scroll pane a field:
   - `private val transcriptScrollPane = JBScrollPane(transcriptPanel, ...)`
   - Install this field in `init` instead of creating a local scroll pane.
2. Before rebuilding rows, compute whether the viewport is at the bottom:
   - `val shouldStickToBottom = isTranscriptAtBottom()`
   - Use a small threshold (for example `JBUI.scale(24)`) so near-bottom counts as bottom.
3. After `transcriptPanel.revalidate()`/`repaint()`, schedule scroll adjustment with `ApplicationManager.getApplication().invokeLater` or `SwingUtilities.invokeLater`.
4. Scroll to bottom when:
   - the user was already at the bottom before render;
   - the transcript was previously empty;
   - the latest visible update was likely user-submitted text.
5. Do not force scroll to bottom when the user has scrolled up.

### Acceptance Criteria

- While the user watches the live tail, incoming streaming chunks keep the latest content visible.
- If the user scrolls up, subsequent streaming updates do not yank the viewport back down.
- Restored threads initially show the latest transcript tail.

## Phase 2: ACP Raw Output Normalization

### Current Problem

`AcpThreadEventMapper.rawOutputText()` falls back to `JsonElement.toString()` for non-primitive values. That makes the transcript
show protocol wrappers such as `{"content":"..."}` instead of the actual output text.

### Implementation

Update `rawOutputText(rawOutput: JsonElement?)` in `AcpThreadEventMapper`:

1. Preserve current behavior for `JsonPrimitive` strings.
2. For `JsonObject`, try text-like fields in order:
   - `content`
   - `text`
   - `output`
   - `stdout`
   - `stderr`
   - `message`
3. If a text-like field is an object or array, recursively extract text from it.
4. For `JsonArray`, join recursively extracted non-blank text with newlines.
5. Use `toString()` only as the last fallback when no text-like shape is found.

### Tests

Add cases in `AcpThreadEventMapperTest`:

- `rawOutput = JsonObject(mapOf("content" to JsonPrimitive("hello")))` maps to `ToolCallOutput.contentDelta == "hello"`.
- Optional: array/object nested content is flattened into readable text.
- Existing primitive raw output behavior remains unchanged.

## Phase 3: Minimal Row Renderer

### Current Problem

`entryRow(label, title, body, status)` is too generic. It renders all transcript entries as nearly identical label + body blocks,
which makes tool calls, diffs, and plans difficult to scan.

### Implementation

Keep `AgentAcpThreadScreen` as one Swing class for now, but split row construction into explicit helpers:

- `messageRow(message: ThreadMessage)`
- `toolRow(toolCall: ThreadToolCall)`
- `commandRow(command: ThreadCommand)`
- `planRow(plan: ThreadPlan)`
- `diffRow(diff: ThreadFileDiff)`
- `contextRow(context: ThreadContextCompaction)`

Use a shared low-level helper only for common layout:

- label line: small bold foreground help color;
- title/body panel: non-focusable read-only text area;
- optional status suffix in label;
- optional monospace body for tool/command/diff previews.

### Row Requirements

#### Messages

- Label: localized role or `humanize(message.role.name).uppercase()` for the first pass.
- Body: full message text, wrapping enabled.
- Completion: show status only if needed; avoid noisy `completed` for normal user/agent messages if it clutters the UI.

#### Tool Calls

- Label: `TOOL` plus status when present.
- Title priority: `title`, then `command`, then `kind`, then `id`.
- Body sections, each only when non-blank:
  - command;
  - path;
  - output preview;
  - summary;
  - approval status.
- Output body uses monospace font.

#### Commands

- Label: `COMMAND` plus status/exit code when present.
- Title priority: `title`, then `command`, then `id`.
- Body: output preview in monospace.

#### Plans

- Label: `PLAN` plus completed/running status.
- Title: plan title or id.
- Body: one line per item in the format `status - title`.
- Empty plan body should not render a large blank area.

#### Diffs

- Label: `DIFF` plus status.
- Title: path/title/id.
- Body: preview of `newText`; if empty, show a localized empty diff placeholder.
- Do not render full huge patch text in this pass.

#### Context Compaction

- Label: `CONTEXT`.
- Title: title/id.
- Body: summary preview.

## Phase 4: UI-only Preview Limits

### Implementation

Add a helper in `AgentAcpThreadScreen`:

```kotlin
private fun previewText(text: String, maxChars: Int = BODY_PREVIEW_MAX_CHARS, maxLines: Int = BODY_PREVIEW_MAX_LINES): String
```

Suggested initial limits:

- regular body: 16 KiB or 120 lines;
- tool/command/diff output: 8 KiB or 60 lines.

When text is truncated, append a localized suffix such as:

```properties
acp.screen.output.truncated=... output truncated
```

### Acceptance Criteria

- A large `Read File` or diff event no longer turns the entire thread view into one massive block.
- The event log and projection still contain full output.
- The user can still identify which tool ran and see enough output to understand the result.

## Phase 5: Accessibility and UI Hygiene

### Implementation

- Set `inputArea.accessibleContext.accessibleName` via bundle text.
- Transcript row text areas remain read-only and non-focusable, so focus stays predictable.
- Preserve keyboard behavior:
  - Enter sends;
  - Shift+Enter inserts newline;
  - disabled input remains visibly read-only.
- Avoid adding custom accessibility properties to static labels unless UI Inspector shows missing names.

### Bundle Additions

Add localized keys to `EngineBundle.properties` for any new visible text, for example:

- `acp.screen.output.truncated`
- `acp.screen.diff.empty`
- `acp.screen.input.accessible.name`
- optional role labels if we stop deriving them directly from enum names.

## Phase 6: Verification

### Automated

Run after implementation:

```bash
./tests.cmd --module intellij.air.acp.tests --test com.intellij.air.acp.AcpThreadEventMapperTest
```

Run Engine tests if reducer/projection code changes; this plan should avoid that:

```bash
./tests.cmd --module intellij.agent.workbench.engine.tests --test com.intellij.agent.workbench.engine.platform.EngineEventStoreTest
```

Always run `lint_files` for touched Kotlin files.

### Manual Dev IDE Check

Use a restored ACP thread and a live ACP thread:

1. Open a restored thread with existing transcript; verify it initially shows the latest tail.
2. Send a prompt; verify user message appears and the viewport follows the live tail.
3. While the agent streams output, scroll up manually; verify the viewport does not jump back down.
4. Let a tool call produce output; verify the row is readable and not raw protocol JSON.
5. Check a plan update and a diff/tool row if the agent emits them.
6. Verify input focus and Enter/Shift+Enter behavior.

## Suggested Implementation Order

1. Implement `rawOutputText` normalization and tests.
2. Introduce `transcriptScrollPane`, `isTranscriptAtBottom`, and `scrollTranscriptToBottom`.
3. Split row helpers without changing data model.
4. Add preview limits and bundle strings.
5. Run tests and manual dev IDE verification.

## Risks

- Rebuilding all Swing rows on every streaming chunk can still be expensive for very large transcripts. Preview limits reduce the worst UI cost, but virtualization may still be needed later.
- The autoscroll heuristic may need tuning if updates arrive in bursts. Start with a simple near-bottom threshold and adjust based on manual use.
- ACP raw output may contain more shapes than the initial extractor handles. Keep the fallback but add tests whenever a new wrapper appears in logs.
- The minimal renderer is intentionally static. Inline expand/collapse, diff viewer, and approval actions should be follow-up work, not hidden scope in this pass.
