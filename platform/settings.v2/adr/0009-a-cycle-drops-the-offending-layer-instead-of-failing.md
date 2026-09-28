# 0009. A cycle drops the offending layer instead of failing

Date: 2026-05-28

## Status

Superseded by [ADR 0134](./0134-a-read-never-asks-a-provider-for-a-value-so-no-cycle-forms.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A contributor may read settings while it builds its layer, and a layer's `getValue` / `setValue` may read other
settings while it resolves. Both can build new contexts and re-enter the layer machinery, so a cycle is
possible.

## Decision

**The platform tracks in-flight operations on the call chain and breaks a cycle locally.** A contributor that
re-enters itself for the same context has its layer dropped from that stack, and the error is logged; the rest
of the stack builds. A layer whose read or write re-enters itself for the same key is skipped for that
operation, the error is logged, and resolution continues with the next layer.

## Consequences

- One misbehaving contributor or layer cannot break resolution for everything else.
- A dropped layer silently changes the effective value; only the log tells.

## Rejected alternatives

### Fail the whole operation

Safer to notice, but one plugin's cycle would then break every read at that context.
