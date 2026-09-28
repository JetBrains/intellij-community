# 0075. Each locality has its own file

Date: 2026-08-13

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0126](./0126-a-key-reads-only-from-the-layers-of-the-localities-it-accepts.md) and
[ADR 0149](./0149-the-application-layer-has-no-local-locality.md).

## Context

The three localities must be stored somewhere. One mixed file could tag each key with its locality, and sync
would then filter per key. Remote dev must replicate team and local, never environmental.

`jb` is a slang word in Chinese, so the names use the full `.jetbrains`.

## Decision

**Each locality is a separate layer with its own file: `.jetbrains.json`, `.jetbrains.local.json` and
`.jetbrains.env.json` for a directory, and the matching `*.local.json` and `*.env.json` siblings for the app and
project layers.**

## Consequences

- Sync is a per-file decision: the platform adds the local and environmental files to `.gitignore`, so only the
  team file goes to VCS, and remote dev ships team and local only. No per-key inspection, no risk of a mis-tagged
  key leaking.
- Co-location needs no check: the environmental file is never replicated.
- A write picks the sibling by locality; a read folds whatever is present, so locality is never a read filter.
- Up to three files per context on disk.
- The resulting behavior is in the [layer locality spec](../spec/layer-locality.spec.md) and the
  [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### One mixed file with per-key tags

Sync becomes per-key filtering, and a mis-tag leaks a value.
