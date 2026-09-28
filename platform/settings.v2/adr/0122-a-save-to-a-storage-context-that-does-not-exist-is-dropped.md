# 0122. A save to a storage context that does not exist is dropped

Date: 2026-09-30

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0150](./0150-a-dropped-save-removes-every-layer-of-its-store.md).

## Context

Without a directory flag ([ADR 0121](./0121-a-file-context-has-no-directory-flag.md)), the file layer cannot tell
whether a path that does not exist is a file or a directory. The save is debounced, so a path can also disappear
between the write and the save. The vault holds the written value in its model
([ADR 0057](./0057-the-vault-is-the-source-of-truth-and-a-provider-is-a-persistence-port.md)), and reads return it.

## Decision

**`LayerProvider.writeData` returns a `SaveResult`: `SAVED` or `DROPPED`. The file layer drops a save when its
storage context is not a directory on disk, and logs a warning with the dropped slices as JSON. On `DROPPED`, the
vault removes the dropped layers from its model and fires the listeners.**

The vault removes the layers only when its model still holds the saved data. A write during the save keeps the save
pending, and the next save decides again.

## Consequences

- The model follows the disk. A dropped value does not stay readable until the next reload.
- A value written for a path that does not exist is lost. The warning keeps it, so a user can restore it by hand.
- The vault removes the layers under its lock, where it also clears the pending save. A push from the provider
  would race with the rule that drops a push while a save is pending.
- The resulting behavior is in the [settings writes spec](../spec/settings-writes.spec.md) and the
  [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### Treat a path that does not exist as a file

The value would go to the per-file entry in the config of the parent. The load ignores a per-file entry for a file
that the directory does not hold, so nobody could read the value after a reload. A save does not keep a per-file
entry for a file that the vault does not hold. So the next save would remove the value, and no warning would keep
it.

### The provider pushes an empty layer after the drop

The push goes through the push channel. The vault drops a push while a save is pending, and the save is pending
during `writeData`. So whether the push survives depends on timing.
