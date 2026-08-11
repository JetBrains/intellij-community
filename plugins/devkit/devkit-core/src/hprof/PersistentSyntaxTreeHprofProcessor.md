# Persistent syntax tree HPROF analysis

This document specifies the behavior of `org.jetbrains.idea.devkit.hprof.PersistentSyntaxTreeHprofProcessor` and the related
`AnalyzePersistentSyntaxTreeOverheadAction` IDE action.

## Scope

The processor inspects JVM HPROF heap dumps using community HPROF parser APIs from `com.intellij.diagnostic.hprof.parser`.
It does not depend on Ultimate profiler internals.

The analysis is focused on persistent syntax tree payload history stored in:

- `com.intellij.psi.impl.source.tree.mvcc.VersionedPayloadMap2`
- `com.intellij.psi.impl.source.tree.mvcc.ArrayVersionedPayloadMap`

## Extraction

`extractVersionedPayloadMaps` returns all discovered instances of the two map implementations.

For `VersionedPayloadMap2`, entries are read from these instance fields:

- `version1`, `payload1`
- `version2`, `payload2`

For `ArrayVersionedPayloadMap`, entries are read from:

- `versions`: a `long[]`
- `payloads`: an object array with the same length as `versions`

Null payload ids are ignored for graph roots and are reported as null payloads in the extraction result.

## Overhead model

`analyzePersistentSyntaxTreeOverhead` estimates retained stale-version overhead relative to the latest payload versions stored in
the extracted maps.

For each extracted map:

- The latest version is the maximum version value in that map.
- Payloads with the latest version are live payloads.
- Payloads with a version smaller than the latest version are stale payloads.
- If multiple entries have the maximum version, all of their payloads are treated as live.

The retained overhead set is computed from heap graph reachability:

```text
retained overhead objects = objects reachable from stale payloads - objects reachable from live payloads
```

The implementation uses a map-aware form of this rule so that map containers do not accidentally make their own stale entries live.

## Map-aware reachability

All extracted maps are considered map graph nodes.

Live traversal starts from map objects that are still live containers. When traversal reaches such a map, it follows only:

- the map structural objects, such as `ArrayVersionedPayloadMap`'s `versions` and `payloads` arrays;
- the payloads for the map's latest version.

For live `ArrayVersionedPayloadMap` structural arrays, traversal includes the arrays themselves but does not traverse from the
payload array into every array element. This prevents stale entries stored in a live map container from becoming live merely because
the container stores historical payload references.

Stale traversal starts from stale payloads of live map containers.

If stale traversal reaches another extracted map object, and that map is not reachable from any latest payload traversal, that map
is classified as a stale-only nested map. Stale traversal then starts from the stale-only map object itself, so the calculation
includes:

- the stale-only map object;
- its structural objects;
- all objects reachable from that map, including its latest payload, because the entire map is retained only by stale history.

If the same nested map is reachable from any latest payload traversal, it remains live and only its stale payloads can contribute to
overhead.

Top-level extracted map objects are not counted as stale overhead merely because they contain historical entries. A map object's own
size is counted only when the map is stale-only reachable through another stale version graph.

## Heap graph and size model

The processor builds a lightweight heap graph from HPROF records:

- instance objects: object reference fields are outgoing edges;
- object arrays: non-null elements are outgoing edges;
- primitive arrays: no outgoing edges.

Class dumps, static fields, GC roots, and dominator trees are not used. This is a specialized reachability comparison for persistent
syntax tree payload history, not a full JVM retained-size or dominator analysis.

Object sizes are shallow sizes estimated from HPROF record data using `HprofObjectSizeLayout`:

- instance size: `objectPreambleSize + estimated JVM instance data size`, aligned to `objectAlignment`
- object array size: `arrayPreambleSize + elementCount * referenceSize`, aligned to `objectAlignment`
- primitive array size: `arrayPreambleSize + elementCount * primitiveElementSize`, aligned to `objectAlignment`

The default layout uses `objectPreambleSize = 8`, `arrayPreambleSize = 12`, `referenceSize = 8`, and `objectAlignment = 8`,
matching the existing community HPROF histogram convention while making compressed-reference layouts configurable.

## Result

`PersistentSyntaxTreeOverheadAnalysis` contains:

- the original map extraction result;
- retained overhead bytes;
- retained object count and ids;
- stale and live root counts;
- stale-reachable and live-reachable object counts;
- stale-only nested map object ids;
- retained objects grouped by class.

## IDE action

`AnalyzePersistentSyntaxTreeOverheadAction` is registered as the internal action `DevKit.AnalyzePersistentSyntaxTreeOverhead` under
`Internal | DevKit`.

The action:

1. asks the user to select an `.hprof` heap dump;
2. runs the overhead analysis with cancellable background progress;
3. opens a generated plain-text report in the editor.

The report includes extraction counts, retained overhead totals, reachability counts, stale-only nested map ids, and a space-aligned
class histogram.
