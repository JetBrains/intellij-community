# 0073. Precedence is context, then locality, then provider

Date: 2026-08-13

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0028](./0028-layers-order-by-context-first-and-anchors-break-ties.md).

## Context

[ADR 0028](./0028-layers-order-by-context-first-and-anchors-break-ties.md) ordered layers by context and broke
ties with anchors. The prototype orders providers by extension order instead, and the three layer kinds
([ADR 0076](./0076-settings-have-three-layer-kinds-a-write-goes-to-local-by-default-and-team-editing-is-a-per-machine-mode.md)) add a locality axis.

## Decision

**A key resolves by a lexicographic order: context, narrowest first; then locality, environmental over local over
team; then provider order, as the last tiebreaker.**

## Consequences

- A local override beats a team default whatever source produced either, and an environmental value wins
  outright.
- The most specific place a user touched always wins.
- In practice only `local > team` fires for one key, since a key's locality admits either environmental or the
  other two.
- The resulting behavior is in the [layer resolution spec](../spec/layer-resolution.spec.md) and the
  [layer locality spec](../spec/layer-locality.spec.md).

## Rejected alternatives

### Provider before locality

One source's shared default could shadow another source's personal override.

### Locality before context

A project-level file could outrank a directory-level customization.
