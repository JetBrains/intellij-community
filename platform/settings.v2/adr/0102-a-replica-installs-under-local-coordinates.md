# 0102. A replica installs under local coordinates

Date: 2026-09-10

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The vault keys layers by provider. Provider instances and extension order are local to one JVM, so they cannot
cross the wire as they are.

## Decision

**A replica provider installs what it receives under its own local coordinates. The origin and replica pair
shares one agreed id, a constant both classes know, so a request says which layer it wants.** Order comes from each
side's extension order.

## Consequences

- No platform-wide provider id and no transferable order.
- The pair id belongs with the other shared code.
