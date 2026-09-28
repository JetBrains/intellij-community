# 0040. Data migration runs on the layer-load lifecycle

Date: 2026-07-14

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Moving data from v1 to v2, or evolving a payload's shape, has to happen at a moment when the old data is
available for its scope. The engine already asks providers for their data per scope.

## Decision

**Data migration hooks the layer-request lifecycle: a provider's `requestData(scope)` fires on app start (app
scope), project open (project scope), and VCS or content change (directory scope), and those are the moments to
import or transform data for that scope.**

## Consequences

- No separate trigger machinery.
- Registration, ordering, idempotency and a possible live v1 and v2 bridge are still open.
