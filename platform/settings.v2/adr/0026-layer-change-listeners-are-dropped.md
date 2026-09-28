# 0026. Layer change listeners are dropped

Date: 2026-06-18

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Listeners on a layer's raw changes were meant for an external integration that mirrors a layer out, Rider
writing `.DotSettings`. After the Rider redesign ([ADR 0024](./0024-resharper-settings-join-as-inert-layers-only-while-editing.md)),
ReSharper owns its layers
and persists them itself.

## Decision

**There are no layer change listeners. They come back only with a concrete case.**

## Consequences

- One API fewer to design and keep.
- Consumers observe effective values only.
- The resulting behavior is in the [change listeners spec](../spec/change-listeners.spec.md).
