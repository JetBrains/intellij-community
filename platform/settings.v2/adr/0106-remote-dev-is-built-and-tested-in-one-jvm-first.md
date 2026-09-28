# 0106. Remote dev is built and tested in one JVM first

Date: 2026-09-10

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The remote-dev design never lets a read cross the wire ([ADR 0084](./0084-each-side-has-its-own-vault-and-every-read-is-local.md)). The real
protocol and the product
modules are the part with no local rehearsal.

## Decision

**Almost all of remote dev lands and is tested in one JVM: two vaults connected by an in-process loopback link.
The transport and the product modules come last.** Every step compiles on its own, keeps the monolith working and
carries its own tests.

## Consequences

- The model is proven before the protocol exists.
- The loopback link stays as the test harness.
