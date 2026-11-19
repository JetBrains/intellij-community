---
name: Frontend File Contexts
description: Requirements for building a file context on a remote-dev frontend from the backend content roots.
style: plain-1
targets:
  - ../shared/src/com/intellij/platform/settings/v2/impl/rpc/SettingsContentRootsApi.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/contentRoots.kt
  - ../../../../remote-dev/settings/v2/backend/split/src/com/intellij/platform/settings/v2/impl/rpc/BackendContentRootsApi.kt
  - ../../../../remote-dev/settings/v2/backend/split/src/com/intellij/platform/settings/v2/impl/rpc/SettingsRemoteApiProvider.kt
  - ../../../../remote-dev/settings/v2/frontend/split/src/com/intellij/platform/settings/v2/impl/FrontendSettingsContextFactory.kt
  - ../../../../remote-dev/settings/v2/frontend/split/src/com/intellij/platform/settings/v2/impl/rpc/FrontendContentRootService.kt
  - ../../../../remote-dev/settings/v2/frontend/split/src/com/intellij/platform/settings/v2/impl/rpc/FrontendContentRootsActivity.kt
---

# Frontend File Contexts

Status: Draft
Date: 2026-09-28

## Summary

In remote development, the frontend has no project model of its own. To build a file context it needs the
content roots of the backend project. The backend sends them over RPC. The frontend then builds a file context with
the same root url and relative path as the backend. The project part differs, because each side keeps its own
project identity. This is the first remote-dev part of settings v2.

## Goals

- Give a file a context on each side that maps to one known context on the other side.
- Keep the frontend roots current when the backend roots change.

## Non-goals

- Mirroring layers and writes between the two sides. It is not built.
  See [ADR 0084](../adr/0084-each-side-has-its-own-vault-and-every-read-is-local.md) and
  [ADR 0095](../adr/0095-the-first-remote-dev-target.md).

## Requirements

- The backend must send the topmost content roots of a project, then each later state of them. It computes them
  with the same function as its own roots cache.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedRootsTest.kt
  [@test] ../../../../remote-dev/settings/v2/lambdaTests/src/com/intellij/platform/settings/v2/lambdaTests/FrontendFileContextsTest.kt
- The backend must not send two equal states in a row.
- The frontend must start listening for the roots when the project opens, so the roots arrive as soon as possible.
  Until they arrive, a file gets the project context.
- A frontend file under a root must get a file context with the backend root url and the relative path.
  See [ADR 0107](../adr/0107-the-connection-carries-a-live-table-of-content-roots.md) and
  [ADR 0127](../adr/0127-a-file-context-anchors-on-the-backend-root-url-on-both-sides.md).
  [@test] ../../../../remote-dev/settings/v2/lambdaTests/src/com/intellij/platform/settings/v2/lambdaTests/FrontendFileContextsTest.kt
- The frontend must match a file against a root by path, not by url, since the two sides use other protocols.
  [@test] ../../../../remote-dev/settings/v2/lambdaTests/src/com/intellij/platform/settings/v2/lambdaTests/FrontendFileContextsTest.kt
- A frontend file under no root must get the project context, as on the backend.
  [@test] ../../../../remote-dev/settings/v2/lambdaTests/src/com/intellij/platform/settings/v2/lambdaTests/FrontendFileContextsTest.kt
- Before the first roots arrive, every frontend file must get the project context.
- A non-persistent file must resolve to a persistent file with the same steps as on the backend. See
  [ADR 0151](../adr/0151-a-non-persistent-file-resolves-through-its-delegate-then-its-parent.md) and the
  [Settings Contexts](settings-contexts.spec.md) spec.
  [@test] ../../../../remote-dev/settings/v2/lambdaTests/src/com/intellij/platform/settings/v2/lambdaTests/FrontendFileContextsTest.kt
- A directory must get the same identity on both sides, except the project part. The thin client reports every
  file as a file, with no parent, so the identity must not depend on that.
  See [ADR 0121](../adr/0121-a-file-context-has-no-directory-flag.md). The project part differs by design. See
  [ADR 0119](../adr/0119-a-project-context-identity-is-the-location-hash-not-the-project-id.md).
  [@test] ../../../../remote-dev/settings/v2/lambdaTests/src/com/intellij/platform/settings/v2/lambdaTests/FrontendFileContextsTest.kt

## Open Questions / Risks

- No test checks that the roots start at project open. The factory also starts them on its first use, so a test
  passes without the startup activity. A missing activity only makes the early file contexts coarser.
- No test covers two requirements: no two equal states in a row, and the project context before the first roots.
  An equal state changes nothing that a caller can observe, and the first roots arrive too soon for a test to hold
  them back. Both need a unit test of a split module, which has no test module yet.
- The project identity differs between the two sides by design. See
  [ADR 0103](../adr/0103-a-side-stores-only-its-own-context-identities.md) and
  [ADR 0119](../adr/0119-a-project-context-identity-is-the-location-hash-not-the-project-id.md). No side sends its
  project identity. A side that receives a context puts its own project into it, found from the `ProjectId` of the
  call. See [ADR 0143](../adr/0143-a-receiving-side-puts-its-own-project-into-a-context.md). No context crosses the
  sides yet, so this translation is not built.
  See [ADR 0104](../adr/0104-context-translation-touches-no-vfs.md).
- These modules exist only in the ultimate repository. Their paths above resolve only there.
