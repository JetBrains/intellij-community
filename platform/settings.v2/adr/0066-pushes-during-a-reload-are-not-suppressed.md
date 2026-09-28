# 0066. Pushes during a reload are not suppressed

Date: 2026-07-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0123](./0123-a-push-during-a-reload-waits-until-the-reload-installs.md).

## Context

On a checkout the file watcher fires while the reload rescans, so its pushes are redundant with the reload.

## Decision

**Spontaneous pushes during a reload are applied. The only push suppression is the per-`(context, provider)`
pending-save one.**

## Consequences

- A push is idempotent and sets the ready flag, so it can only help.
- A genuine concurrent update is never dropped to a race.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).
