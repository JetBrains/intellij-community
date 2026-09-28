# 0141. A context has no editing aspect

Date: 2026-10-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0018](./0018-the-editing-aspect-is-part-of-a-contexts-identity.md).

## Context

[ADR 0018](./0018-the-editing-aspect-is-part-of-a-contexts-identity.md) gives a context a `userEditing` aspect and
makes it part of the identity. Its only reason is the ReSharper layers, which joined the stack only while the user
edits settings ([ADR 0024](./0024-resharper-settings-join-as-inert-layers-only-while-editing.md)).

ReSharper settings are now their own keys and layers, in normal program flow
([ADR 0140](./0140-resharper-settings-are-their-own-keys-and-layers.md)). Nothing creates an editing context.

## Decision

**A context has no editing aspect. Its identity holds no editing flag.**

## Consequences

- A settings UI and normal program flow read the same layers at the same context.
- The identity layout changes, so the snapshot cache `VERSION` goes up. An old cache is ignored once.
- A future need for layers that exist only while editing needs a new decision.
- The resulting behavior is in the [settings contexts spec](../spec/settings-contexts.spec.md).
