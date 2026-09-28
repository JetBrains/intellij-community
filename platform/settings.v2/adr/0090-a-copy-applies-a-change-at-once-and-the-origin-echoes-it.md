# 0090. A copy applies a change at once, and the origin echoes it

Date: 2026-09-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A copy is writable ([ADR 0085](./0085-mirroring-is-symmetric-a-layer-has-an-origin-and-copies.md)), but the origin
owns the write rules. Waiting for the origin before showing a write would make the frontend UI lag. An origin
loads its settings whenever it starts, and a copy attaches whenever it wants.

## Decision

**An origin applies a change and sends it to every copy. A copy applies a change at once, sends it to the origin,
and the origin applies it and sends it back: the echo confirms or corrects the copy. A whole layer goes to copies
on connection, as the origin holds it at that moment, and again whenever it arrives from the provider. A change
carries its source, and only a change that started from a local mutation travels on.**

## Consequences

- The copy's UI shows a write at once; the value is speculative until the echo lands.
- No diffing: a provider hands over a whole layer, and a whole layer is what mirrors.
- The loop guard needs a source on every change.
