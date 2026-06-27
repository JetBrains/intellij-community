# Agent Session Provider Guide

This guide is for implementing or reviewing an Agent Workbench provider. Specs define product behavior; this file explains the provider
entry point and source-level contracts that let shared services consume a provider safely.

## Provider Entry Point

A provider has one normal Agent Workbench registration: `com.intellij.agent.workbench.sessionProvider`. The registered descriptor owns the
provider identity, display metadata, launch behavior, and one stable `sessionSource`:

```kotlin
private class ExampleAgentSessionProviderDescriptor : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider = AgentSessionProvider.from("example")
  override val sessionSource: AgentSessionSource = ExampleSessionSource()
  // launch, display, and CLI availability members omitted
}

private class ExampleSessionSource : BaseAgentSessionSource(provider = AgentSessionProvider.from("example")) {
  override suspend fun loadThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return readExampleIndex(path)
  }
}
```

The source owns provider-local session discovery state. Optional provider behavior is discovered by checking whether this same source also
implements focused capability interfaces such as `AgentSessionUpdateSource` or `AgentSessionCostSource`.

Keep constructors cheap. Do not start watchers, launch CLIs, read large indexes, or bind project state from a source constructor. Use lazy
services, flows, or refresh methods for expensive work.

## Required Source Contract

`AgentSessionSource` has one required behavior: list active, non-archived threads for a normalized Agent Workbench path.

- `provider` must match every returned `AgentSessionThread.provider`.
- Thread ids must be concrete provider ids that stay stable across IDE restarts.
- `updatedAt` is epoch milliseconds from provider state. Consumers use it for ordering, read/unread, cost cache invalidation, and stale
  update filtering.
- Titles should be normalized for UI, but do not invent mutable titles that will churn on every refresh.
- `listThreads(path, openProject)` returns active rows only. Archived rows belong to `AgentSessionArchivedSource`.
- `openProject` is non-null only when `path` is currently open in the IDE. Closed project and worktree discovery must still work without it.
- `canReportExactThreadCount` should be `false` when discovery is intentionally capped or backend-limited.

Prefer `BaseAgentSessionSource` when the provider participates in read/unread state. Override `loadThreads`; the base class keeps the public
listing entry point final and gives refresh implementations the `refreshThreadsByListing` fallback.

## Optional Capabilities

Do not add no-op methods to `AgentSessionSource`. Implement a focused capability on the provider source only when the provider can satisfy
its contract. Consumers discover support by type, so missing interfaces mean unsupported behavior.

| Capability                                  | Implement when                                                                                                      |
|---------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `AgentSessionPrefetchSource`                | The provider can batch-load complete active snapshots for several paths more cheaply than listing each path.        |
| `AgentSessionArchivedSource`                | Archived rows are available separately and can be listed accurately for a path.                                     |
| `AgentSessionUpdateSource`                  | The provider emits background path or thread updates for loaded rows or hints.                                      |
| `AgentSessionActiveThreadUpdateSource`      | An active chat tab can watch one concrete thread with provider-specific filtering.                                  |
| `AgentSessionRefreshSource`                 | The provider can refresh more precisely than full path listing, especially for thread-scoped updates.               |
| `AgentSessionRefreshHintsSource`            | The provider can cheaply fetch non-authoritative hints for pending-tab rebinding or presentation patches.           |
| `AgentSessionCostSource`                    | Visible and archived rows can hydrate cost after rows are known.                                                    |
| `AgentSessionThreadOutlineSource`           | Persisted history can be exposed as a read-only outline without restoring a terminal.                               |
| `AgentSessionThreadOutlineNavigationSource` | A live provider view can navigate to stable outline item anchors.                                                   |
| `AgentSessionThreadOutlineForkSource`       | The provider can create a new thread from a specific outline item without mutating the source thread.               |
| `AgentSessionReadStateSource`               | The provider tracks read/unread state relative to open chat tabs. `BaseAgentSessionSource` already implements this. |

## Why Capabilities Are Not Extension Points

Core provider capabilities stay on the provider-owned source instead of becoming separate EPs. Separate capability EPs would repeat
`providerId`, split caches and watchers across registrations, make provider lifetime unclear, and create partial-registration states that
consumers would have to reconcile.

Use additional EPs only when the implementation is truly external or cross-cutting, such as UI contributors, feature settings, launch
contributors, or provider-specific fallback contributors. They are advanced hooks, not the standard provider implementation path.

## Update Events

Use `AgentSessionUpdateSource.updateEvents` for provider-wide signals and `AgentSessionActiveThreadUpdateSource.activeThreadUpdateEvents`
for one open concrete thread. Emit only meaningful changes after filtering raw filesystem or backend noise.

- `THREADS_CHANGED` means rows may have been added, removed, archived, or changed in a way that needs authoritative refresh.
- `HINTS_CHANGED` means presentation, activity, or rebind hints changed without requiring a full row snapshot.
- Scope events with `scopedPaths` and `threadIds` whenever the provider knows them. Narrow scopes reduce refresh work and make active tabs
  update sooner.
- Set `mayHaveChangedProjectFiles` or `changedProjectFilePaths` only when provider evidence says project files might have changed. Use
  absolute file paths when exact files are known.
- Do not emit an event for every persistence write if parsed thread state is unchanged.

## Refresh Results

`AgentSessionRefreshSource.refreshThreads` returns an `AgentSessionSourceRefreshResult`.

- `completeThreadsByPath` is authoritative for that path and replaces all active rows for the provider there.
- `partialThreadsByPath` updates only returned thread ids and must not evict other rows.
- `removedThreadIdsByPath` removes rows during partial refresh.
- `failuresByPath` reports path-local failures while allowing other paths in the same request to succeed.
- Throw cancellation, but prefer path-local failures for ordinary parse, I/O, or backend errors.

Providers that only support complete path listing can implement `AgentSessionRefreshSource` and delegate to
`refreshThreadsByListing(request)` from `BaseAgentSessionSource`.

## Outlines, Navigation, And Forks

`AgentSessionThreadOutlineSource.loadThreadOutline` reads persisted history. It must not launch or restore terminals, send provider input,
drive TUIs, or mutate provider state. Return `null` when unavailable; return an empty outline when outlines are supported but no visible
items exist.

Navigation and fork capabilities require stable outline item ids. `canNavigateThreadOutlineItem` and `canForkThreadFromOutlineItem` are
runtime gates, not static support flags. Return `true` only when the current provider state can execute the operation now.

Forking must leave the source thread unchanged. Return an `AgentSessionOutlineForkResult` with the new thread and any provider-specific
launch override needed to open it.

## Cost Hydration

`AgentSessionCostSource.loadThreadCosts(path, threads)` runs after visible active or archived rows are known. Key results by concrete thread
id. A missing key or `null` value means unavailable cost. Cache internally when parsing is expensive, and invalidate by `updatedAt` rather
than recomputing on every visibility refresh.

## Provider Checklist

- Register one provider descriptor through `com.intellij.agent.workbench.sessionProvider`.
- Keep descriptor metadata localized through bundle keys and use stable provider ids.
- Keep source constructors cheap and side-effect free.
- Put source-owned capabilities on `sessionSource`; use contributor EPs only for external or cross-cutting additions.
- Return active and archived rows through separate capabilities.
- Use complete snapshots only when they are truly authoritative.
- Emit scoped update events, not raw watcher noise.
- Preserve stable ids and epoch-millis timestamps.
- Make outline item ids stable before enabling navigation or fork capabilities.
- Run provider tests, session service tests, chat outline tests when applicable, and `ApiCheckTest` after changing provider APIs.
