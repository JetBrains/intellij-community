# 00H6KQ. The settings save of the IDE flushes the pending saves of the vault

Date: 2026-11-27T12:01:39Z

## Status

Accepted ([IJPL-261041](https://youtrack.jetbrains.com/issue/IJPL-261041)).

## Context

The vault saves a write after a debounce ([ADR 0061](./0061-the-vault-persists-on-a-debounced-save-signal.md)).
Until the save runs, a config file on the disk is older than the model. `flush` forces the pending saves of a
scope ([ADR 0080](./0080-flush-drains-a-scope-from-the-vault.md)). The close of a project and the close of the
application call it.

A project config file is under version control. Git must read the current file at a commit, in the list of the
changes, and at an update of the project. A user who leaves the IDE to run Git in a terminal needs the same.

The IDE already has a moment for this. It runs its settings save before a commit, before a refresh of the Changes
view and before Update Project. It also runs the save when its frame loses the focus and when it is idle. The XML
settings of a `PersistentStateComponent` reach the disk there. The component store calls each created service
that implements `SettingsSavingComponent` inside that save.

## Decision

**The settings save of the IDE flushes the pending saves of the vault, through the `SettingsSavingComponent` hook
of the component store.** A save of the application flushes the application scope, which covers every pending
save. A save of a project flushes the scope of that project: the project layer and the file configs under it.

## Consequences

- A config file is current on the disk at each moment when the XML settings of the IDE are.
- A flush with nothing pending must be cheap, because the idle save runs often.
- The close of a project and the close of the application keep their own flush. A close can run with no settings
  save, and they also save the snapshot cache.
- An operation that runs no settings save still races the debounce: a checkout, a merge, a rebase or a reset that
  starts in the IDE. The XML settings have the same gap.
- On a side that holds copies, a flush also drains the peer
  ([ADR 0086](./0086-a-flush-on-a-side-with-copies-also-drains-the-peer.md)). A settings save there then waits
  for the link, also the idle save.
- The resulting behavior is in the [settings writes spec](../spec/settings-writes.spec.md).

## Rejected alternatives

### A listener of the VCS operations

The freeze listener of the VCS and the topic of a batch file change run before a checkout, a rebase and an update.
Neither one runs before a commit, and neither one covers Git outside the IDE. Each one needs VCS knowledge in the
settings engine. The settings save covers the commit and the focus loss with no VCS code.

### A commit check that flushes

It covers the commit only, and the list of the changes is already computed when it runs.
