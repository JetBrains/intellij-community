# 00GVJL. A refresh on a VFS event reads the config file from the VFS and every other read takes the disk

Date: 2026-11-27T08:03:22Z

## Status

Accepted ([IJPL-262273](https://youtrack.jetbrains.com/issue/IJPL-262273)).
Amends [ADR 0167](./0167-a-config-file-enters-the-vfs-through-an-asynchronous-refresh.md).

## Context

The application, project and file layers refresh a config file when a VFS event for it comes.
[ADR 0167](./0167-a-config-file-enters-the-vfs-through-an-asynchronous-refresh.md) states that the watcher reads
the file from the disk at that moment, so the read sees the current content.

That is not true for a save from the IDE editor. The VFS writes the content of a file to the disk asynchronously
(`AsyncableLocalFileSystemImpl`). A save queues the disk write and fires the VFS event at once. A user opens
`app.settings.json` in the editor, changes a value and saves the document. A log showed the order: the event, the
refresh 1 ms later, and the end of the disk write 15 ms after that. During the write the file was empty for a
moment. The refresh read the old text, the vault saw no change, and the edit was lost until a reload of the vault.
The project config file has the same race, and its refresh got the new text only by timing. The platform asks a VFS
listener not to read the disk directly, for this reason.

The disk is still the right source outside a VFS event, because the VFS content can be older than the disk there.
One case is an edit while the IDE was down. The other case is the own write of a layer. It goes to the disk through
`java.nio`, and it reaches the VFS later through an asynchronous refresh
([ADR 0167](./0167-a-config-file-enters-the-vfs-through-an-asynchronous-refresh.md)).

A layer keeps one config file per locality. The refresh after an event of one file read every file of the layer.

## Decision

**A refresh that a VFS event starts reads the config file from the content that the VFS holds, and every other
read takes the disk.** The load at the start, a load that the vault requests and a reload of the vault read the
disk as before.

The refresh reads and pushes only the file that the event names. A file of another locality has no event, so its
VFS content can be stale after an own write, and the refresh must not read it.

The VFS read applies only when the VFS already knows the file and the file is valid. The lookup runs no refresh
and no blocking lookup, so the rule of
[ADR 0167](./0167-a-config-file-enters-the-vfs-through-an-asynchronous-refresh.md) stays. In every other case the
refresh reads the disk: the file is unknown to the VFS, it is not valid, or its content cannot be read.

The rule covers every layer that keeps its values in a JSON config file: the application layer, the project layer
and the file layer. A store that is not a file ignores it.

## Consequences

- A save of a config file from the IDE editor reaches the settings at once.
- An external edit is not affected. The VFS loads the content of a changed file from the disk, so the VFS read
  returns the text of the disk.
- The echo of an own write is not affected. Its VFS event comes after the refresh of the file, and the VFS content
  then equals the disk.
- An event of one config file no longer refreshes the files of the other localities of the layer.
- The refreshes run one at a time for each file, as
  [ADR 0180](./0180-the-refreshes-of-one-storage-unit-run-one-at-a-time-through-a-conflated-queue.md) says for a
  storage unit.
- A refresh reads a file that the VFS dropped, such as a deleted one, from the disk. The file is absent there, so
  the layer is empty, as before.
- Two sources exist now. A new caller of the read must know whether it runs on a VFS event.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### Always read the content of the VFS

The load at the start would need the file in the VFS first, which is a blocking lookup at the application load.
The VFS content is also stale after an own write, until the asynchronous refresh lands.

### Read the disk after a delay

No delay is long enough for certain, and each refresh would wait for it.

### Refresh every locality, and read the files without an event from the disk

It keeps one refresh for each layer. The refresh would need to know which file has the event, to choose the
source of each read. With that knowledge, the read of the other files is work with no reason: they did not change.

### Wait for the pending disk write of the VFS, then read the disk

`ManagingFS.flushPendingUpdates` waits for the write. The disk then stays the only source. It blocks the refresh
until the write ends, the API is experimental, and the platform asks a VFS listener to stay on the VFS level.
