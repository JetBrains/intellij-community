# 0149. The application layer has no local locality

Date: 2026-10-08

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0075](./0075-each-locality-has-its-own-file.md).

## Context

[ADR 0075](./0075-each-locality-has-its-own-file.md) gives each locality its own file, for a directory, the
application and a project alike. So the application layer has a team file, a local file and an environmental file.

The local locality keeps a value out of VCS for one user, next to the shared team file of a project or a directory.
The application config is not in a project and never goes to VCS. It belongs to the machine already. The
environmental locality still has a reason there: remote development may send the application layer to the other
side, and never sends an environmental layer.

## Decision

**The application layer has the team and the environmental locality, with `app.settings.json` and
`app.settings.env.json`. It has no local locality.**

## Consequences

- A plain write of a shareable key at the application context lands in `app.settings.json`. It finds no local
  layer there, so it takes the team layer, as the write fallback says.
- The project and the file layers keep all three localities.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md) and the
  [layer locality spec](../spec/layer-locality.spec.md).
