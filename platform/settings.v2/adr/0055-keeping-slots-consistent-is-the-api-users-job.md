# 0055. Keeping slots consistent is the API user's job

Date: 2026-07-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0012](./0012-renaming-or-removing-a-slot-is-the-owners-job-and-the-platform-broadcasts-it.md).

## Context

[ADR 0012](./0012-renaming-or-removing-a-slot-is-the-owners-job-and-the-platform-broadcasts-it.md) had the
platform broadcast slot renames across contexts to every contributor. There is no good generic way to keep a
slot consistent across the places it is used. A profile defined at the app level and used per project has no
single owning config to rewrite, no reachable set of all its users (closed projects, branches not checked out),
and a rename would also need every stored discriminator value fixed.

## Decision

**The platform does not attempt scope- or context-aware slot consistency. It exposes the primitives,
`SettingsMapKey.copySlot`, `dropSlot` and `renameSlot`, and the caller decides where to apply them and how to keep
its data coherent.**

## Consequences

- No cross-context broadcast in the platform.
- A feature with slots used in many places must handle renames itself.
- Reinforces [ADR 0051](./0051-slots-have-no-reference-integrity-and-no-vcs-journal.md): the engine does not chase
  a stored discriminator.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
