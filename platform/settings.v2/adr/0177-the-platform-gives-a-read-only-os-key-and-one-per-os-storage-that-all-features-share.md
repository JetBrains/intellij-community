# 0177. The platform gives a read-only OS key and one per-OS storage that all features share

Date: 2026-11-17

## Status

Accepted ([IJPL-261944](https://youtrack.jetbrains.com/issue/IJPL-261944)).

## Context

Some values belong to one operating system, for example the name of a keymap. A shared config or a mirrored config
can carry such a value to a machine with another OS, where it is wrong.

- A map key holds the slots of one dimension in ordinary layers
  ([ADR 0010](./0010-multidimensional-settings-are-slots-in-a-layer-not-extra-layers.md)).
- A discriminator value is a stable id, not a label
  ([ADR 0056](./0056-a-discriminator-value-is-a-stable-id-not-a-label.md)).
- A read-only key never consults a layer ([ADR 0011](./0011-a-readonly-key-never-consults-a-layer.md)). The
  platform already has one such discriminator, `LANGUAGE`.
- A write names its slots ([ADR 0088](./0088-a-write-names-its-slots.md)).

## Decision

**The API module declares the read-only key `OS` and the map key `PER_OS_SETTINGS`, whose discriminator is `OS`.
Every feature puts its OS-bound keys into this one storage.**

- `OS` returns the id of the current operating system: `windows`, `macos`, `linux`, `freebsd`, `ohos` or `other`.
- A child key of `PER_OS_SETTINGS` reads the slot of the current OS. A read pinned to another OS reads the slot of
  that OS.
- A write pins the slot, as for any map key, with `context.accessFor(key)`.
- A child key of a plugin can live in this storage of the platform. Each child keeps its own identity in the slot.
- The platform promotes both keys.

## Consequences

- A config has one split by the OS, under one entry. Each OS slot holds the values of all the features.
- A team config can hold the values of several OS at once, and each machine reads its own.
- A feature that adopts the storage moves its old values into the slot of the current OS. The other OS slots stay
  empty until a machine of that OS writes them.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).

## Rejected alternatives

### A map key per feature

Each feature would add an OS split of its own. A config would then hold many splits by the OS, and the same OS
slots would repeat under each feature.

### The environmental locality for OS-bound keys

The environmental locality is for a value of one machine, such as a path. It keeps the value out of VCS and out of
the other remote-dev side. A keymap name is right on every machine with the same OS, so two such machines could not
share it.
