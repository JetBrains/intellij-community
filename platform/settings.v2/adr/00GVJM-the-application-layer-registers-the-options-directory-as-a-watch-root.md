# 00GVJM. The application layer registers the options directory as a watch root

Date: 2026-11-27T08:03:23Z

## Status

Accepted ([IJPL-262273](https://youtrack.jetbrains.com/issue/IJPL-262273)).

## Context

The application layer reloads its config files in the config `options` directory when a VFS event for them comes
([ADR 0167](./0167-a-config-file-enters-the-vfs-through-an-asynchronous-refresh.md)). A VFS event for an edit from
outside the IDE needs two steps. The native file watcher reports the changed file, and the VFS marks it dirty. The
VFS refresh that the IDE starts when its frame gets the focus then checks the dirty files and fires the event.

The native file watcher reports only the files under a registered watch root. The platform registers the roots of
a project, so the project layer and the file layer are covered. Nothing registered the config directory. So an
external edit of `app.settings.json` left no dirty mark, the refresh skipped the file, and the settings kept the
old value until a manual reload from the disk or a restart. A test with a real IDE frame showed it: with no watch
root, the value stayed old after 18 focus gains and 12 refreshes.

## Decision

**The application layer registers the config `options` directory as a flat watch root for its lifetime.** The root
is not recursive, because the config files of the layer are direct children of the directory. The layer never
removes the root. The service of the layer lives until the application ends, and a removal at that moment comes
after the file watcher stops, which fails with an error.

## Consequences

- An external edit of an application config file reaches the settings when the IDE frame gets the focus, as an
  external edit of a project file does.
- The edit still waits for a VFS refresh. An edit while the IDE keeps the focus arrives at the next focus gain.
- On Linux the native file watcher ignores a root that does not exist. When the `options` directory is absent at
  the first start, the watcher picks it up at its next update of the roots.
- A headless IDE runs no native file watcher and no refresh on a focus gain, so the root has no effect there.
- The native file watcher watches one more directory. Every file of the `options` directory that the VFS knows
  now gets its external changes reported, not only the settings v2 files.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### Check the config files on the disk when the IDE gets the focus

It needs no VFS, but it adds a second trigger next to the VFS watcher, with its own change detection. The watch
root reuses the path that the project files already take.

### Watch the directory with a file system watch service of the JDK

It reports an edit with no focus gain. It adds a second watcher next to the native one, and on macOS the stock
implementation polls. [ADR 0167](./0167-a-config-file-enters-the-vfs-through-an-asynchronous-refresh.md) rejected
a second watcher too.
