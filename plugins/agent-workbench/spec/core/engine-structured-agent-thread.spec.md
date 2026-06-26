---
name: Engine Structured Agent Thread
description: Requirements for the permanent split between terminal-backed Agent Chat providers and Engine structured thread providers, including the Engine event log, transcript projection, and ACP runtime adapter boundary.
targets:
  - ../../engine/src/core/*.kt
  - ../../engine/src/platform/*.kt
  - ../../engine/src/ui/*.kt
  - ../../engine/resources/intellij.agent.workbench.engine.xml
  - ../../chat/src/AgentChatCustomContent.kt
  - ../../chat/src/AgentChatFileEditor.kt
  - ../../../../../plugins/agent-workbench/acp/src/*.kt
  - ../../../../../plugins/agent-workbench/acp/resources/intellij.agent.workbench.acp.xml
---

# Engine Structured Agent Thread

Status: Draft
Date: 2026-06-26

## Summary

Agent Workbench has two permanent runtime families. Terminal-backed providers (`claude`, `codex`,
`opencode`, `junie`, and similar CLIs) continue to own their embedded-terminal Agent Chat lifecycle.
Structured Workbench providers render an IDE-native thread by writing canonical events into the Engine log
and reducing them into `ThreadProjection`: the materialized read model used by the UI. The first
production structured provider is `acp`; mock, remote, and future structured runtimes are Engine runtime
kinds behind a structured provider, not a reason to migrate terminal-backed providers onto the Engine
transcript model. Shared Workbench surfaces (session list, editor tab identity, prompt launch, activity,
and outline) must support both families.

`ThreadProjection` is an event-sourcing term in this spec. It means the current thread state computed from
the ordered `ThreadEventEnvelope` log by the Engine reducer: transcript entries, status, approvals,
activity inputs, outline source data, and side state. It is not the source of truth, not a persisted provider
history format, and not a contract that terminal-backed providers must implement.

## Goals

- Keep terminal-backed providers first-class and permanent; they must not be treated as a migration
  backlog for the structured thread model.
- Make Engine the owner of structured thread state: canonical event log, reducer, rich transcript
  projection, and IDE-native thread screen.
- Keep ACP as a runtime adapter that normalizes ACP protocol updates into Engine events; ACP must not own
  the generic transcript model or UI row model.
- Preserve provider-neutral Workbench surfaces by projecting each provider family into existing session,
  tab, activity, and outline contracts.

## Non-goals

- Replacing terminal-backed Agent Chat tabs with Engine UI for existing CLI providers.
- Requiring terminal providers to emit `ThreadEventEnvelope`s or expose `ThreadProjection`s.
- Moving provider-specific terminal history parsers into Engine.
- Making AUI events or `Acp2ToAUIConverter` the foundation for Agent Workbench structured threads.

## Requirements

- Agent Chat must permanently route by provider capability. Providers with an
  `AgentChatCustomContentProvider` render custom IDE-native content; providers without one use the
  existing embedded-terminal lifecycle. This routing is not a fallback or a temporary migration path.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Structured provider identity and Engine runtime identity are separate. Workbench tab/session identity is
  always built from `AgentSessionProvider` plus the provider-owned thread id; `RuntimeKind` and runtime ids
  are Engine metadata used inside the projection, not substitutes for Workbench provider identity. Shared
  chat, sessions, and outline code must treat thread ids as opaque and must not infer routing from prefixes
  such as `acp:`. Future structured providers must register an explicit provider descriptor/custom-content
  provider, or deliberately reuse an existing structured provider id as a product decision.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

- Terminal-backed providers must remain valid without any Engine event store record or
  `ThreadProjection`. Opening, restoring, archiving, and prompt dispatch for terminal providers must
  continue through their existing provider descriptors, launch specs, session sources, and terminal
  controllers.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

- The Engine module owns structured thread state only. Its source of truth is the ordered
  `ThreadEventEnvelope` log; UI and outline state are derived by reducing the log into
  `ThreadProjection`. Engine event types and projection models must stay in the `engine` module and must
  not reference ACP protocol classes.
  [@test] ../../engine/testSrc/EngineEventStoreTest.kt

- Engine APIs and documentation must not imply that existing terminal-backed CLI providers are expected to
  implement `AgentRuntime`, emit Engine events, or surface through `ThreadProjection`. Any
  `RuntimeKind.Terminal` or terminal-like Engine event is limited to structured-runtime terminal content,
  debug/migration experiments, or legacy mirrors that are explicitly filtered out of normal terminal
  provider Agent Chat surfaces.
  [@test] ../../engine/testSrc/EngineEventStoreTest.kt

- `ThreadProjection` must expose a rich ordered transcript for structured threads. The visible order is a
  list of stable entries, while updates are applied by stable ids such as `messageId`, `toolCallId`,
  `terminalId`, `diffId`, and `planId` rather than by UI row index. The projection must support user
  messages, assistant messages, agent thoughts/work, tool calls, terminal command/output content, file
  diffs, live and completed plans, context compaction entries, approval state, token/cost usage,
  available commands, mode, and config-option side state as the corresponding events become available.
  Existing simple message accessors may remain as compatibility views, but the rich transcript entries are
  the primary structured-thread UI contract.
  [@test] ../../engine/testSrc/EngineEventStoreTest.kt

- Tool approvals are part of tool-call state. An approval request for a tool call must survive subsequent
  tool-call updates, and resolving the approval must update the same tool-call entry instead of only
  decrementing a global counter. The reducer must preserve the tool call's title/kind/command/output,
  attach `requested` approval state by `toolCallId`, and later replace it with `approved`, `rejected`, or
  `expired` resolution state without reordering the tool-call transcript entry.
  [@test] ../../engine/testSrc/EngineEventStoreTest.kt
  [@test] ../../../../../plugins/agent-workbench/acp/testSrc/AcpThreadEventMapperTest.kt

- Terminal command content inside structured threads is modeled as structured tool/command content, not as
  a separate Agent Chat terminal tab. Output received before the corresponding terminal/tool entry is
  created must be buffered or otherwise replayed so transcript order and terminal content remain coherent.
  [@test] ../../engine/testSrc/EngineEventStoreTest.kt

- Reducer behavior must be deterministic and replay-safe. Replaying the same ordered event log after IDE
  restart must produce the same transcript, side state, outline source, and activity state. Late updates for
  unknown stable ids must either create a pending entry that is completed when the start event arrives, or
  be kept as debug/audit metadata; they must not be attached to the wrong visible entry.
  [@test] ../../engine/testSrc/EngineEventStoreTest.kt

- `AgentSessionThreadOutline` remains a provider-neutral projection, not a live transcript model. Terminal
  providers build outlines from their provider-specific persisted history/parsers; the Engine provider
  builds outlines from `ThreadProjection` entries. Missing Engine projection support for a terminal
  provider must not affect its outline support.
  [@test] ../../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../../engine/testSrc/EngineEventStoreTest.kt

- ACP support lives in the `plugins/agent-workbench/acp` adapter. It launches/prepares ACP sessions,
  receives `SessionUpdate`s and ACP client callbacks, and records canonical Engine events. It must not
  directly construct Swing rows or AUI events for Agent Workbench structured thread UI.
  [@test] ../../../../../plugins/agent-workbench/acp/testSrc/AcpThreadEventMapperTest.kt

- `Acp2ToAUIConverter` and related AUI converters may be used as references for protocol edge cases
  (streaming chunks, tool-call merging, permissions, diffs, MCP output, terminal metadata), but they must
  not become the architectural dependency for Engine structured threads.

- Launch keeps two permanent paths: terminal providers use `AgentSessionTerminalLaunchSpec` and terminal
  prompt dispatch; Engine/ACP launches use out-of-band launch and preallocated Engine thread ids as
  described in `../launch/engine-acp-launch-profiles.spec.md`.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

- Restore keeps the same permanent split. Restoring a structured tab installs custom Engine content and
  rebuilds its visible state from the Engine event log; restoring it must not start an embedded terminal,
  synthesize a terminal launch spec, or fall back to a terminal tab when the structured runtime is missing.
  Missing projection data, missing adapter support, and failed runtime reconnects must render explicit
  structured-thread states owned by Engine/adapter code.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

## User Experience

- Terminal-backed chats continue to look and behave like embedded terminal sessions, including provider
  TUI behavior and terminal readiness-gated prompt dispatch.
- Structured Engine chats open in the same Agent Chat editor surface but render IDE-native transcript rows
  for messages, thoughts, tools, approvals, plans, diffs, and terminal output.
- Session rows, editor tab identity, activity badges, and structure view outline should remain consistent
  across both families even though their backing state models differ.

## Data & Backend

- Engine event payloads are canonical Workbench payloads. Runtime-specific data may be preserved as raw or
  debug references, but reducer-visible fields must use Engine names and types.
- `ThreadProjection` is scoped to the Engine provider family. It must not be moved into `sessions-core` or
  made part of the `AgentSessionSource` contract.
- ACP runtime ids, protocol ids, and metadata are normalized at the adapter boundary before they reach the
  Engine reducer.
- Terminal-provider persisted history and Engine structured event logs may coexist for the same project
  path without deduplicating across provider families; canonical thread identity remains
  `provider:threadId`.

### ACP Restore And Load-First Continuation

ACP restore uses three separate persisted surfaces:

- Agent Chat tab state restores the editor tab identity only: provider `acp`, project path,
  `threadIdentity`, Engine `threadId`, last known title, and activity. Restoring this state must open the
  Engine custom-content screen and must not synthesize a terminal launch spec.
- The Engine event log restores local visible chat state. The screen may render immediately by replaying
  `events.jsonl` into `ThreadProjection`, even before an ACP process is connected.
- ACP runtime binding restores whether the agent can be contacted again. The binding must include the ACP
  agent identity and the server-issued `agentSessionId` once the runtime returns one. It may also include
  non-secret launch context such as cwd, remote branch, or organization id when required by the ACP
  implementation.

ACP continuation is load-first. On restored structured chats, the adapter should initialize the ACP client,
read the agent capabilities, and continue only when a stored `agentSessionId` exists and the runtime reports
`loadSession` support. In that case it calls ACP `loadSession(agentSessionId, sessionParameters)`. Any ACP
`session/update` history replayed by `loadSession` is not appended to the Engine log as fresh transcript events:
the local Engine event log is already the durable transcript source. After the session is loaded, only new live
updates from subsequent prompt turns are mapped back into canonical Engine events.

Automatic fallback from `loadSession` to ACP `resumeSession` is intentionally not part of restored Workbench
ACP semantics. `resumeSession` has different behavior and may reconnect transport without replaying the
session history expected by the Engine transcript. If a restored chat has an ACP binding but the runtime does
not support `loadSession`, or loading fails, the Engine transcript remains visible from the local event log but
prompt input, approvals, and reconnect actions must be disabled until the user explicitly starts a new
session/continuation flow.

For brand-new ACP chats with no stored `agentSessionId`, the adapter may create a new ACP session. After the
session is acquired, it must persist the server-issued session id in the ACP runtime binding before accepting
future restore/load continuation as available.

#### ACP Persistence Implementation Plan

The persistence layer should reuse the Engine JSONL event log as the durable source of truth. Workbench must not
add a separate ACP-specific session-state file for structured chats: visible transcript state and reconnectable
runtime binding are both reconstructed from `ThreadProjection`.

Two events feed the binding: `ThreadCreated` carries the binding *seed* known at prepare time (`agentId`, `cwd`),
and `RuntimeSessionBound` carries the *server-issued* `agentSessionId` once a session exists. Prepare does not write
`RuntimeSessionBound`, because the server session id is unknown until `newSession`/`loadSession` returns.

1. Add persisted runtime binding state to the Engine projection.
   - Add `RuntimeSessionBound` to `ThreadEventType`.
   - Add `ThreadDisconnected` to `ThreadEventType` and reduce it to `ThreadStatus.Disconnected` (the status enum value
     already exists; only the event type and its reducer branch are new). The default `else` branch of `reduce` would
     otherwise drop the event silently.
   - Add `ThreadRuntimeBinding` to the Engine core model with `runtimeKind`, optional `agentId`, optional
     `agentSessionId`, optional `cwd`, optional `remoteBranch`, and optional `organizationId`. The model fields are
     optional because the projection aggregates a partial seed (from `ThreadCreated`) with the later full binding;
     `remoteBranch` and `organizationId` are reserved headroom and are not written by the ACP steps below.
   - Add `runtimeBinding: ThreadRuntimeBinding?` to `ThreadProjection`.
   - `agentId` stays an opaque string in the Engine core: the engine must not depend on `AcpAgentId` or any other
     adapter type. Parsing/validating it is the adapter's job (step 4).
   - Reduce both `ThreadCreated` and `RuntimeSessionBound` into `runtimeBinding` as side state only, never as a
     transcript entry. Repeated events merge known fields and preserve previously known values when a new event omits
     them; in particular the reducer must never overwrite a known `agentSessionId` with `null`.
   - The binding is always reduced into side state regardless of `EventVisibility`. `AuditOnly` only hides an event
     from the user-visible transcript; it must not exclude the event from replay or from `runtimeBinding`.

2. Seed the binding when a chat is prepared.
   - `AcpSessionManager.prepare(threadId, entry)` continues to write `ThreadCreated` and `ThreadWaiting`.
   - `ThreadCreated` must carry `runtimeKind = Acp`, `agentId = entry.id.fullId`, and the non-secret launch context
     available at prepare time, especially project `cwd`. The reducer seeds these into `runtimeBinding`.
   - The agent identity must be the stable ACP catalog id, not the display name selected in launch UI.

3. Persist the ACP server session id after session acquisition.
   - `AcpSession.ensureConnected(...)` writes `RuntimeSessionBound` immediately after `newSession` or `loadSession`
     returns a `ClientSession`, before streaming any prompt output, so a crash leaves the smallest possible window in
     which a live server session is unknown to the log (this gap is best-effort, not transactional).
   - The event must include `agentSessionId = session.sessionId.value`, plus the same `agentId` and `cwd` carried by
     the seed so the merged binding stays complete.
   - Prompt dispatch for future turns is only restart-safe after this event has been written.

4. Restore ACP ownership from the Engine projection.
   - `AcpSessionManager.handles(threadId)` stays a cheap lookup over already-rehydrated adapter state. It must not
     read the Engine projection, touch the JSONL store, or query the ACP catalog.
   - Rehydration must run eagerly when the structured tab is restored (when custom content is installed), not lazily
     inside `handles()`/`EnginePromptSender.forThread` on the Swing render path: reading the event log and the catalog
     there would put disk I/O and catalog resolution on EDT for every re-render.
   - The eager restore hook must be an Engine-owned extension boundary, implemented by the ACP module. The community
     Engine UI/custom-content provider may call an `EngineRuntimeConnector`-style extension for the thread, but it must
     not depend directly on `AcpSessionManager` or ACP catalog classes.
   - The connector reads `ThreadProjection.runtimeBinding` off EDT, requires `runtimeKind == Acp`, parses `agentId`
     with `AcpAgentId.tryToParse`, looks up the entry in `AcpAgentsCatalog`, caches the resolved runtime owner in the
     ACP adapter, then notifies the Engine screen through the existing Engine change path so prompt input can be
     re-enabled without blocking rendering.
   - Distinguish "catalog not yet ready" from "agent unavailable". If the `AcpAgentsCatalog` is still warming up, the
     tab stays read-only but must re-evaluate ownership once the catalog is ready; only a binding that is missing,
     malformed, or points to a genuinely unavailable agent is a terminal read-only state. Either way the screen may
     render the local Engine projection, but prompt sending stays disabled until ownership is resolved.
   - The screen gates prompt input on both ownership and status: input is disabled when no `EnginePromptSender` handles
     the thread or when the thread status is `Disconnected`, even if an adapter still owns the thread.

5. Implement load-first continuation in the ACP session startup path.
   - `AcpSession.ensureConnected(...)` reads the restored `ThreadRuntimeBinding` from
     `EngineProjectService.projection(threadId).runtimeBinding` before creating the ACP session.
   - After `client.initialize(...)`, inspect `AgentCapabilities.loadSession` from the initialize response.
   - If `agentSessionId` exists and the runtime supports `loadSession`, call
     `client.loadSession(SessionId(agentSessionId), sessionParameters, operationsFactory)` with the same
     `ClientSessionOperations` factory used for `newSession`.
   - After a successful `loadSession` or explicit recovery from `Disconnected`, record `RuntimeSessionBound` and then a
     lifecycle event (`ThreadWaiting` when no prompt is running, or `ThreadStarted` for the prompt that triggered the
     reconnect) so the projection can leave `ThreadStatus.Disconnected`.
   - If `agentSessionId` exists but the runtime does not support `loadSession`, record `ThreadDisconnected` and do not
     call `resumeSession` or `newSession` automatically.
   - If no `agentSessionId` exists for a brand-new prepared chat with no prior transcript, create a new ACP session and
     persist the acquired id as in step 3.
   - If no `agentSessionId` exists for a restored chat that already has local transcript/history, treat it as a legacy
     pre-binding thread. Do not start a process during tab restore, but once ACP ownership is rehydrated the adapter may
     recover the projection to `ThreadWaiting` so the user can explicitly continue. The first prompt in that state
     creates a fresh ACP session, persists its `RuntimeSessionBound`, and does not replay the local transcript into the
     new server session.

6. Do not duplicate replayed history on continuation.
   - The local event log already contains the transcript from the original session, so the `session/update` stream
     replayed by `loadSession` must not be appended again as fresh events.
   - Replayed `loadSession` history arrives through the `ClientSessionOperations` client callbacks (`notify(...)`), not
     through `session.prompt(...).collect`; today `notify` is a no-op, so replay is already non-persisted. The guard is
     to keep load-replay updates out of the Engine log: if `notify`-delivered updates ever start persisting, exclude
     those produced during the `loadSession(...)` call. Output collected from `session.prompt(...).collect { ... }` is
     live prompt-turn output and persists normally.
   - Dedupe by protocol `messageId`/`toolCallId` is not sufficient for text deltas: replaying the same `MessageDelta`
     with the same `messageId` would still append duplicate content in the reducer. If a future ACP protocol exposes an
     explicit load-replay boundary or stable event ids, the adapter may use that to persist only the live tail.

7. Avoid recording undeliverable user messages.
   - `AcpSessionManager.sendPrompt(...)` keeps the submitted text in memory while it resolves the ACP entry and runs
     `ensureConnected(...)`/load-first continuation. This is where capability checks and `loadSession` failure can be
     observed.
   - Only after the ACP runtime session is connected should it append the user's `MessageDelta`/`MessageCompleted` to
     the Engine log and call `session.prompt(...)`.
   - If the restored runtime cannot be contacted, record `ThreadDisconnected` and do not append a user prompt that was
     never delivered to the agent.
   - Because the user's message is only appended after the session connects, the first prompt / reconnect shows a brief
     gap before the message renders. An optimistic echo that displays the pending text before connection is an optional
     product decision, not required by this plan.

8. Cover the behavior with focused tests.
   - Engine reducer tests verify that `ThreadCreated` seeds `runtimeBinding`, that `RuntimeSessionBound` survives
     JSONL replay (including `AuditOnly` binding events), and that repeated binding events merge without erasing an
     existing `agentSessionId`.
     [@test] ../../engine/testSrc/EngineEventStoreTest.kt
   - A new ACP restore test (e.g. `AcpSessionManagerRestoreTest`) verifies that the ACP connector rehydrates ownership
     from persisted binding data, `handles(threadId)` observes only the rehydrated in-memory owner, and
     `sendPrompt(...)` does not append a user message when the runtime cannot be contacted.
   - ACP startup tests verify that stored `agentSessionId` plus `loadSession` support uses `loadSession`, that missing
     `loadSession` support does not fall back to `resumeSession` or `newSession`, that a restored transcript without an
     `agentSessionId` does not call `newSession`, and that a `loadSession` replay does not duplicate the existing
     transcript.
   - Connector tests cover catalog warm-up: a restored tab with a valid binding stays read-only while the catalog is not
     ready, then rehydrates ownership and enables sending once the matching ACP entry appears.
   - Existing chat restore tests continue to prove that restored structured ACP tabs never synthesize a terminal
     launch spec.
     [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

### Canonical Event Payloads

- Event payloads consumed by the reducer must be specified in Engine terms before adapter-specific tests
  rely on them. Every reducer-visible payload shape must have a stable id field when it can be updated by
  later events, and may include `rawRef` or debug metadata only outside the reducer-visible contract.
- Lifecycle events:
  - `ThreadCreated`: optional `title`, optional `runtimeKind`, optional `runtimeId`; for ACP threads it also
    carries the binding seed fields `agentId` and `cwd`, which the reducer folds into `runtimeBinding`.
  - `ThreadStarted`, `ThreadWaiting`, `ThreadCompleted`, `ThreadFailed`, `ThreadDisconnected`, `ThreadCancelled`:
    status transitions; completed/failed/disconnected events may carry `summary` or `message`. `ThreadDisconnected`
    represents a recoverable runtime binding problem such as unavailable agent, missing `agentSessionId`, unsupported
    `loadSession`, or load failure; it keeps the local transcript visible but disables prompt input.
- ACP runtime binding events:
  - `RuntimeSessionBound` (to be added before implementation): required `runtimeKind`, required
    `agentSessionId`, optional `agentId`, optional `cwd`, optional `remoteBranch`, optional
    `organizationId`. It is written only after a server session id exists (the prepare-time seed travels on
    `ThreadCreated`). This event is adapter-visible side state used to decide whether restored input can be
    enabled; it is reduced into `runtimeBinding` regardless of `EventVisibility` and is not a transcript entry by
    itself.
- Message events:
  - `MessageDelta`: required `messageId`, optional `role` (`User`, `Agent`, `System`, `Runtime`, `Tool`),
    required `contentDelta`; deltas append to the same transcript entry by `messageId`.
  - `MessageCompleted`: required `messageId`; marks the same entry complete without changing order.
- Tool and command events:
  - `ToolCallStarted`: required `toolCallId`, optional `title`, `kind`, `command`, `path`, and
    runtime-normalized metadata.
  - `ToolCallOutput`: required `toolCallId`, required `contentDelta`, optional `stream` (`stdout`,
    `stderr`, `structured`, `debug`).
  - `ToolCallFinished`: required `toolCallId`, optional `status`, `exitCode`, `summary`.
  - `CommandStarted`, `CommandOutput`, and `CommandFinished` are used only when a structured runtime
    exposes terminal-like content that is not already represented as a tool call; they use `terminalId` as
    the stable id and follow the same start/output/finish semantics.
- Approval events:
  - `ApprovalRequested`: required `toolCallId`, optional `approvalId`, `title`, `kind`, and decision
    options normalized to Engine names.
  - `ApprovalResolved`: required `toolCallId`, optional `approvalId`, required `decision` (`approved`,
    `rejected`, `expired`), optional `reason`.
- Plan, diff, usage, command catalog, mode, and config-option events must define their stable ids and
  reducer-visible fields in this section before they are rendered in the Engine UI.

## Error Handling

- A structured runtime connection failure records an Engine failure or disconnected event for that structured thread;
  it must not trigger terminal restore or terminal launch fallback for the same tab. Runtime binding and restore
  failures that leave the local transcript valid should prefer `ThreadDisconnected` over `ThreadFailed`.
- Missing structured runtime support must leave terminal providers unaffected. A product build without the
  ACP module must still list and launch terminal providers normally.
- A restored structured tab whose Engine event log is missing or unreadable must render an Engine-owned
  missing-thread state and offer only structured recovery actions (for example close/retry when available);
  it must not create a blank Draft projection that looks like a valid thread.
- A structured thread whose adapter is absent may still show persisted projection data read-only when the
  Engine log is available. Sending prompts, approvals, or reconnect actions must be disabled until an
  adapter that handles the thread is present.
- Unknown or unsupported structured events should be ignored or recorded as debug/audit metadata without
  corrupting the visible transcript projection. If an unknown event references a known stable id, the
  reducer must preserve enough debug context to diagnose the dropped update.

## Testing / Local Run

- `./tests.cmd --module intellij.agent.workbench.engine.tests --test com.intellij.agent.workbench.engine.platform.EngineEventStoreTest`
- `./tests.cmd --module intellij.agent.workbench.acp.tests --test com.intellij.agent.workbench.acp.AcpThreadEventMapperTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatFileEditorLifecycleTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionLaunchServiceTest`

## Open Questions / Risks

- The current Engine projection is intentionally small; expanding it to a rich transcript should be done
  incrementally while keeping compatibility accessors for the existing simple message UI.
- The payload schema above intentionally leaves plan, diff, usage, mode, command catalog, and config-option
  events incomplete until the first reducer tests for those entries are added. Those fields must be
  specified here before UI rows depend on them.
- `AgentAcpThreadScreen` is an MVP name. The target concept is an Engine structured thread screen, so code
  should avoid making ACP part of generic UI/model names as the implementation is generalized.

## References

- `../launch/engine-acp-launch-profiles.spec.md`
- `../chat/agent-chat-editor.spec.md`
- `../chat/agent-chat-structure-view.spec.md`
- `../sessions/agent-sessions.spec.md`
