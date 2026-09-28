# 0080. Flush drains a scope from the vault

Date: 2026-08-18

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0013](./0013-flush-is-a-full-barrier-over-every-contributor.md).

## Context

[ADR 0013](./0013-flush-is-a-full-barrier-over-every-contributor.md) made flush a full barrier over every
contributor. Since then the vault owns persistence ([ADR 0061](./0061-the-vault-persists-on-a-debounced-save-signal.md)): pending writes sit
in its debounced savers,
not in providers.

## Decision

**`SettingsVault.flush(scopeContext)` forces every pending save the scope covers to disk now, skipping the
debounce, and suspends until written.** It drains by context, not by provider, so a project flush also reaches
file configs beneath it. The vault drains on app shutdown, and the project layer service drains its project on
close. A per-unit write mutex keeps a flush and a saver from writing one file at once.

## Consequences

- Shutdown and project close lose no pending write.
- The external API stays, for branch switch and pre-commit, which cancel nothing.
- Callers must quiesce writes; a write arriving after the drain starts is not guaranteed to be included.
- The resulting behavior is in the [settings writes spec](../spec/settings-writes.spec.md).
