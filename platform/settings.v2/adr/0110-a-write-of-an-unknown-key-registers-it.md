# 0110. A write of an unknown key registers it

Date: 2026-09-24

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Only a promoted key is known to the registry. A plugin can forget to promote a key. Checking on every write and
logging each time would flood the log. `SettingsKey.equals` compares `(pluginId, name)`, so it cannot tell the
same instance from a different key with the same identity.

## Decision

**The registry maps an identity to one key instance, compared by reference. A write waits for discovery and looks
the key up. A missing key registers there, the error is logged once, and the write applies. A key whose identity
belongs to another instance fails the write. A key of a plugin no longer loaded does not register.** A read
consults nothing.

## Consequences

- A forgotten promotion costs one error, not a broken feature.
- A value never lands where another key answers.
- One lookup per write is free next to the lock and layer copy a write already takes.
- The resulting behavior is in the [key registry spec](../spec/key-registry.spec.md).
