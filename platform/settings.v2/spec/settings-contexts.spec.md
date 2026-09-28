---
name: Settings Contexts
description: Requirements for the context a setting is read from or written to, its chain, and its access overlays.
style: plain-1
targets:
  - ../src/com/intellij/platform/settings/v2/SettingsContext.kt
  - ../src/com/intellij/platform/settings/v2/SettingsAccessContext.kt
  - ../src/com/intellij/platform/settings/v2/ContextFactory.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsContexts.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/BaseSettingsContextFactory.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/contentRoots.kt
  - ../backend/src/com/intellij/platform/settings/v2/impl/BackendSettingsContextFactory.kt
  - ../backend/src/com/intellij/platform/settings/v2/impl/SettingsRootsCache.kt
---

# Settings Contexts

Status: Draft
Date: 2026-09-28

## Summary

A context is the place a setting is read from or written to: the application, a project, or a file or directory.
Each context has a parent, so the contexts form a chain from a file up to the application. A read at a context
resolves along this chain, and the narrowest context that holds a value wins. An access context is an overlay that
pins the slot of a multidimensional setting without changing the context.

## Goals

- Give settings file-level granularity.
- Make a context safe to keep: it holds no live project or file.
- Let a caller read or write a slot other than the selected one.

## Non-goals

- Which layers exist at a context. The [Layer Resolution](layer-resolution.spec.md) spec owns that.
- How a frontend builds a file context. The [Frontend File Contexts](frontend-file-contexts.spec.md) spec owns that.

## Requirements

### The chain

- The application context must be the root of every chain. A project context parents to it.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsContextTest.kt
- A file under a content root must chain through each directory up to that root, then to the project.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsContextTest.kt
- A file under nested content roots must anchor on the topmost root.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedRootsTest.kt
- The chain must reach every directory between the file and its topmost root.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedRootsTest.kt
- A file under no content root must get the project context, with no file node.
  See [ADR 0125](../adr/0125-a-file-under-no-content-root-gets-the-project-context.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedRootsTest.kt
- A file inside a jar under the project must get the project context.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedRootsTest.kt
- A non-persistent file must resolve to a persistent file before it gets a context. A persistent file is not a
  light file. Until the platform reaches one, it repeats these steps. See
  [ADR 0151](../adr/0151-a-non-persistent-file-resolves-through-its-delegate-then-its-parent.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNonPersistentFileTest.kt
  - A `VirtualFileDelegate` must give its delegate.
  - A `VirtualFileWindow` must give its delegate, which is the host file.
  - Any other non-persistent file must give its parent.
- A non-persistent file must get the project context when a step gives no file. See
  [ADR 0125](../adr/0125-a-file-under-no-content-root-gets-the-project-context.md) and
  [ADR 0151](../adr/0151-a-non-persistent-file-resolves-through-its-delegate-then-its-parent.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsContextTest.kt
- A non-persistent file must get the project context when its steps loop back to a file.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNonPersistentFileTest.kt
- A context built from a `PsiFile` must pin the language of the file across its whole chain.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsContextTest.kt

### Content roots

- The anchor roots must be the project content roots with every nested root dropped, sorted.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsRootCollapsingTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedRootsTest.kt
- A root that only shares a name prefix with another root must survive.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsRootCollapsingTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedRootsTest.kt
- The anchor roots must be computed again after the project roots change.

### Identity

- A context must have a string identity. Two contexts for the same target must have equal identities.
- The identity layouts must be `app`, `project|<projectId>` and `file|<projectId>|<rootUrl>|<relativePath>`.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsContextTest.kt
- A context must hold strings only, never a live `Project` or `VirtualFile`.
  See [ADR 0081](../adr/0081-a-context-carries-no-validity-check.md).
- A file context identity must hold the project, the anchor root url and the relative path. It must not hold a
  directory flag, so a directory gets the same identity on the frontend and on the backend, except the project
  part. See [ADR 0121](../adr/0121-a-file-context-has-no-directory-flag.md). The project part differs by design.
  See [ADR 0119](../adr/0119-a-project-context-identity-is-the-location-hash-not-the-project-id.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedRootsTest.kt
- A project context identity must hold `Project.locationHash`, not the `ProjectId`.
  See [ADR 0119](../adr/0119-a-project-context-identity-is-the-location-hash-not-the-project-id.md).
- The platform must rebuild a context from its identity with no VFS access. A malformed identity gives `null`, and
  the platform logs an error for it, because the platform never builds one. An empty project id or root URL is
  malformed. The relative path may be empty: it is empty for a content root.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsContextTest.kt
- A context must carry no validity check. A stale context resolves to whatever layers exist.
  See [ADR 0081](../adr/0081-a-context-carries-no-validity-check.md).
- A context must report its kind: application, project, or file. A directory reports the file kind.
- A context identity must hold no editing flag.
  See [ADR 0141](../adr/0141-a-context-has-no-editing-aspect.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsContextTest.kt

### Access contexts

- `access { }` must pin discriminator values on a context and keep its identity and its chain.
  See [ADR 0010](../adr/0010-multidimensional-settings-are-slots-in-a-layer-not-extra-layers.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsAccessContextTest.kt
- The pins must apply to every context up the chain.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsAccessContextTest.kt
- `access` on an access context must refine it in place. A new pin replaces an old pin of the same key.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsAccessContextTest.kt
- An access context with no pins left must collapse to the plain base context.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsAccessContextTest.kt
- `access(a, b)` must pin each named discriminator to the value it resolves to now.
  See [ADR 0088](../adr/0088-a-write-names-its-slots.md).
- `accessFor(key)` must pin every discriminator up the storage chain of the key, outermost first.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsPinnedSlotWriteTest.kt
- A discriminator that a context already pins must keep its pinned value.

## Open Questions / Risks

- Named scopes are postponed, and their place in the chain is open. See
  [ADR 0138](../adr/0138-named-scopes-are-postponed-and-their-place-in-the-chain-is-open.md).
- A user context is postponed. See [ADR 0137](../adr/0137-a-user-context-is-postponed.md).
- Symlinked directories and files are not designed.
