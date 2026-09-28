# 0024. ReSharper settings join as inert layers, only while editing

Date: 2026-06-17

## Status

Superseded by [ADR 0140](./0140-resharper-settings-are-their-own-keys-and-layers.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Rider carries ReSharper's settings, which are layered themselves and consumed by the ReSharper backend, not by
IDEA code. Rider keeps a separate settings UI with its own layer selector for them. The goal is one layer-aware
settings UI for both.

## Decision

**ReSharper registers no keys. It contributes inert layers: each refuses every key through its `keyFilter`,
carries an opaque ReSharper handle, and anchors against the IDEA layer ids so the stacks interleave. The layers
join only in an editing context.** ReSharper keeps its data, storage and resolution. The platform places the
layers and hands each handle back to build the configurable.

## Consequences

- One settings UI can show both layer stacks, with no separate window.
- In normal program flow the layers are absent and cost nothing.
- ReSharper has to push edits live instead of batching them behind an Apply button.

## Rejected alternatives

### Import ReSharper settings as platform keys

The platform would duplicate a model it never uses.
