# 0145. A reload treats a malformed config as missing

Date: 2026-10-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A config file can be malformed, for example while a user edits it. A watcher push of a malformed file sends
nothing, so the vault keeps the layer it holds. A reload of a malformed file returns no layer for it, so the sweep
drops the layer it held ([ADR 0065](./0065-a-reload-evicts-stale-layers-by-mark-sweep.md)). The specs said that a
malformed file always keeps the previous layer, and they called a change during a malformed state not settled.

The rule "keep the old layer" was meant for a push: a watcher must not push an empty layer for a file in the
middle of an edit. A reload is different. It runs when a project opens, and later on a branch switch, so it is a
fresh read of every store.

## Decision

**A reload treats a malformed config like a missing one: it drops the layer of that config.** A push of a malformed
file still keeps the layer that the vault holds.

## Consequences

- The vault never serves values from a config that it could not read on its last reload.
- The next save to that store writes what the vault holds over the malformed file. The half-edited content is lost.
- No snapshot of the old layer is kept to fill the gap. A write on top of an old layer would overwrite the file with
  old content too.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md) and the
  [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### Keep the held layer on a reload too

A write would then save the old layer plus the new value over the file, so the edit is lost anyway, and the vault
serves values that may no longer be in the file.
