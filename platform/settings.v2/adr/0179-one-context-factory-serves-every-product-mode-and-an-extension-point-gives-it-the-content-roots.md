# 0179. One context factory serves every product mode, and an extension point gives it the content roots

Date: 2026-11-19

## Status

Accepted ([IJPL-262093](https://youtrack.jetbrains.com/issue/IJPL-262093)). Amends
[ADR 0166](./0166-the-light-compatible-backend-code-lives-in-the-backend-baseline-module.md).

## Context

A file context anchors on a content root of its project
([ADR 0127](./0127-a-file-context-anchors-on-the-backend-root-url-on-both-sides.md)). Two context factories built
it: one in a backend module, over the project model, and one in a frontend split module, over the roots that the
backend sends ([ADR 0107](./0107-the-connection-carries-a-live-table-of-content-roots.md)).

A light product mode starts with a reduced set of modules. A client light session loads neither of the two
modules, so it has no context factory at all, and every read of a setting fails there.

A feature must work through the same code in every product mode. When the process becomes a backend or a
frontend, the feature gets more, and nothing is replaced.

The factory is a plain function. It runs under a read lock, also on the EDT. So it cannot wait for the roots.

## Decision

**One context factory in the shared module builds the contexts in every product mode. It takes the content roots
of a project from a first-wins extension point of the shared module. With no extension, the base directory of the
project is the only root.**

- The monolith and the backend register an extension that reads the project model and caches the roots.
- The frontend registers an extension that holds the roots from the backend.
- A light product mode registers nothing. The answer for it is plain code in the shared module.
- The first extension always answers. No product mode loads two extensions, so there is no pass to a next one.
- The context factory lives in the shared module. The context dump action lives in the shared essential module,
  because its dialog needs a module outside `core.platform`
  ([ADR 0181](./0181-the-vault-module-depends-only-on-core-platform-modules-and-the-code-that-needs-more-lives-in-the-shared-essential-module.md)).
  The roots cache lives in the backend module, because it reads the project model. The other project-scoped
  machinery stays in the backend baseline module, as [ADR 0166](./0166-the-light-compatible-backend-code-lives-in-the-backend-baseline-module.md) says.
- A file matches a root by path. A file of an archive file system, such as a file inside a jar, is under no root.

## Consequences

- A client light session has a context factory, and a file under the base directory of its project gets a file
  context.
- JetBrains Light, the standalone light mode, anchors on the base directory too. It does not read the roots of
  the project model. So a file under a content root outside the base directory gets the project context there, and
  a project with several roots has one anchor.
- A client light session has no backend, so its anchor is a url of its own. The rule that a frontend anchors on the
  backend root url ([ADR 0127](./0127-a-file-context-anchors-on-the-backend-root-url-on-both-sides.md)) holds for a
  frontend with a backend.
- The context dump action reads the roots through the same lookup. So one action in the shared essential module serves
  every product mode, and a split session shows the action of each side.
- A switch of the product mode only adds an extension. The roots of a file context change with it. A context that
  a caller built before the switch keeps its old anchor.
- The match by path serves every side. A frontend file has another protocol than the backend root urls. A backend
  file inside a jar has another protocol than its root too, so a comparison of the protocols cannot tell the two
  sides apart. The file system of the file can.
- The frontend cannot open a file inside a jar, so the archive rule changes nothing there.
- A file of a file system that is not an archive, with a path under the path of a root, gets a file context, also
  when its protocol differs from the protocol of the root.
- The monolith keeps its direct read of the project model, with no wait for the first roots.
- The resulting behavior is in the [settings contexts spec](../spec/settings-contexts.spec.md) and in the
  [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).

## Rejected alternatives

### A flow of the roots in every product mode

The frontend gets the roots as a flow. The same flow in the monolith gives one code path for the source too. But
the monolith then has no roots until the first value arrives, and it sees a change of the roots with a delay. A
caller that builds a context in that time gets the project context and keeps it.

### The local fallback of the lite RPC as the routing

The helper suspends, and so does every method of an RPC interface. The factory cannot suspend. A blocking wait is
not allowed on the EDT, and under a read lock it stops every write.

### A service that a backend or a frontend module overrides

A module that declares a service override cannot load without a restart. The upgrade of a light session loads its
modules without one.

### A context factory in a baseline module for the client light session

It serves only the client shape of a light session. The standalone shape does not load that module, so it needs
one more copy of the factory.
