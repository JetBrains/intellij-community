# 0076. Settings have three layer kinds, a write goes to local by default, and team editing is a per-machine mode

Date: 2026-08-13. Merged: 2026-10-15.

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Some project settings are personal and must not be committed. Today the per-project workspace storage outside the
project tree serves that. Settings as code also invites a personal file next to the shared one.

A binary split into shared and non-shared data mixes two things: a personal override that should follow the user,
and a machine-bound value (an absolute path) that must never leave the machine. The dominant enterprise case is a
developer tweaking some committed settings for themselves, not reconfiguring the shared project.

A plain write to local leaves no way to write to team, which breaks
[ADR 0072](./0072-a-delegated-job-comes-with-a-mechanism.md). A committed opt-out setting would flip the mode for
the whole team.

## Decision

**Three layer kinds: shared (committed to VCS), local (per-user, not committed, the tier Settings Sync syncs), and
environmental (machine-bound, never leaves the machine, never syncs). An unhinted write goes to the local layer.**
Agreed with Daniil Ovchinnikov.

**An environmental app and project key, `EDIT_TEAM_LAYER`, flips shareable writes to team-first.** Because it is
environmental, it is a per-machine mode, not a committed setting. A per-write locality selector, pinned on the
access context, is postponed.

## Consequences

- Personal tweaks stay out of the committed config by default.
- The devops path works: sit down, switch the mode, edit the team settings.
- A local-to-team "move this setting" quickfix cannot ship until the per-write selector exists.
- The resulting behavior is in the [layer locality spec](../spec/layer-locality.spec.md).

## Earlier decisions

### 0021, 2026-06-17: non-shared project data lives in external storage or a `.local` sibling

A layer kept project data non-shared in one of two ways: in per-project external storage outside the working tree,
or in an in-tree sibling with a `.local` name (`settings.json` shared, `settings.local.json` local) that the
platform keeps gitignored. Each layer chose. External storage was invisible to the working copy, and a `.local`
file travelled with the working tree but was never committed. There were two mechanisms for one need, chosen per
layer. 0069 replaced the binary split with three layer kinds.

### 0069, 2026-08-03: settings have three layer kinds, and a project-wide setting flips the default

It set up the three layer kinds and the local default of the decision above. It also let a setting, project-wide
or wider, flip the default to shared-first. It left open the precedence between the kinds, the mapping of "share
this" and "keep local" hints, and which keys may be environmental. The current decision replaced the setting with
the per-machine `EDIT_TEAM_LAYER` mode, because a committed setting would flip the mode for the whole team.
