# 0056. A discriminator value is a stable id, not a label

Date: 2026-07-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Slot renames are the hard part of slot consistency ([ADR 0055](./0055-keeping-slots-consistent-is-the-api-users-job.md)). They happen
because the discriminator value
is often a user-visible label, such as a profile name, and users rename labels.

## Decision

**Use a stable, immutable id as the discriminator value, not a user-visible label.** This is a recommendation to
API users.

## Consequences

- Renaming the label never touches the slot key, so the rename problem disappears.
- The feature keeps a separate id-to-label mapping.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
