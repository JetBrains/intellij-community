# 0002. Settings Controller bridges PSC and the layered API

Date: 2026-04-14

## Status

Superseded by [ADR 0135](./0135-settings-v2-has-no-settings-controller-bridge.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Settings Controller (IJPL-136) already layers settings transparently: it injects values when a
`PersistentStateComponent` deserializes its state, so the PSC holds a pre-merged flat result. That
transparency is also its limit. It has no layer identity, no targeted writes, no layer-aware UI, and no
context, so folder-level settings cannot work through it.

The new layered API is key-based, context-aware and layer-aware. Moving every PSC to it at once is not
possible.

## Decision

**Settings Controller and the new layered API coexist, in different roles.** The layered API is the target
API. Settings Controller becomes the backward-compatibility bridge: new layers join its chain as a
`DelegatedSettingsController`, so existing PSC consumers get merged values from all layers with no code
change. Layers are the single source of truth for both paths.

A write takes a context (application, project, directory, …) and lands in the first writable layer that
matches it. A caller that needs a specific layer passes a more exact context, or a hint such as
`user-local` for the non-shared project layer.

## Consequences

- No big-bang migration: PSC code keeps working, new code uses the layered API.
- As platform code moves to the new API, it gains layer awareness. Until then, it sees the merged view.
- Two access paths exist for a long time, and both must agree on the layers.

## Rejected alternatives

### Settings Controller as the new API

It cannot express layer identity, targeted writes or folder context, because a PSC is a singleton blob.
