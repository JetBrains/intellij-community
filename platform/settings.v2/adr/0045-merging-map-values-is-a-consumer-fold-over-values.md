# 0045. Merging map values is a consumer fold over `values()`

Date: 2026-07-16

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Resolution is first-hit-wins: the narrowest layer holding a key supplies the whole value. A demo on 2026-07-13
hit a map of objects populated at the app layer and empty at the project layer; a file-context read returned
the empty map. Merge for maps looked required. A merge engine also needs a provenance view, since a merged
value comes from several layers.

## Decision

**The engine exposes each layer's contribution through `key.values(context)`, and a consumer that wants a merge
folds over it. Merge-for-maps and resolution provenance are not engine features.**

## Consequences

- No per-key merge policy, no reducer on the key, no provenance view to build.
- Each consumer that needs a merge writes the fold.
- How a narrower layer removes an inherited entry (empty, absent, or an explicit tombstone) stays open until a
  case needs it.
- The resulting behavior is in the [layer resolution spec](../spec/layer-resolution.spec.md).

## Rejected alternatives

### A per-key merge policy in the engine

More machinery, and it blurs provenance, for what one fold in consumer code does.
