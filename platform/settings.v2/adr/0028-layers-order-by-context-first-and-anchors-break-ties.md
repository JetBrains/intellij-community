# 0028. Layers order by context first, and anchors break ties

Date: 2026-06-19

## Status

Superseded by [ADR 0073](./0073-precedence-is-context-then-locality-then-provider.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A context's stack holds layers from many contributors. Their order decides which value wins. Layers sit at
different contexts of the chain, and several can sit at the same context, such as a plugin's overrides next to the
project layer.

## Decision

**Layers are ordered primarily by the position of their context in the chain, widest to narrowest. Among layers
at the same context, anchors decide: a layer names the layer ids it must sit narrower than.** A read walks from
the narrowest end. An anchor that cannot be satisfied keeps the layer out of the stack, with an error logged.

## Consequences

- A narrower context always wins, whatever the contributor.
- Contributors order themselves against well-known ids, with no central registry of positions.
- A misconfigured anchor silently removes a layer; only the log tells.
