# 0072. A delegated job comes with a mechanism

Date: 2026-08-13

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The design often decides that something is the API user's job: slot consistency, sharing a setting with the team.
With locality, a plain write went to local and there was no way at all to write to team, so "sharing is the
user's action" was a job with no means.

## Decision

**Whenever the platform decides "X is the API user's job", the API user must have a concrete API to do X.**
Every "owner's concern" is valid only together with the mechanism that makes it possible.

## Consequences

- Locality needs a way to write to team before sharing can be the user's job.
- A per-setting local-to-team quickfix cannot ship before a per-write locality exists.
- The resulting behavior is in the [layer locality spec](../spec/layer-locality.spec.md).
