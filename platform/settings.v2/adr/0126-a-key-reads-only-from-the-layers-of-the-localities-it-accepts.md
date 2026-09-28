# 0126. A key reads only from the layers of the localities it accepts

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0075](./0075-each-locality-has-its-own-file.md).

## Context

[ADR 0074](./0074-a-layers-locality-and-a-keys-locality-are-two-enums.md) lets a key declare the localities it
accepts, and a write routes by them. [ADR 0075](./0075-each-locality-has-its-own-file.md) gives each locality its
own file, and says that a read folds whatever is present, so locality is never a read filter.

A file can still hold a key in the wrong locality. For example, a user commits a machine path into the team file,
or edits a shared value into the environmental file by hand.

## Decision

**A key reads only from the layers of the localities it accepts.** A layer of another locality does not exist for
that key, even when it holds a value for it.

## Consequences

- A value in the wrong file has no effect. A machine path in the team file does not apply on the machine of
  another user.
- A read and a write follow the same rule, so a key never reads a value that it could not write.
- The resulting behavior is in the [layer locality spec](../spec/layer-locality.spec.md).

## Rejected alternatives

### Read every layer, as [ADR 0075](./0075-each-locality-has-its-own-file.md) says

A value in the wrong file would apply. A shared file could then set a machine-only value for every user.
