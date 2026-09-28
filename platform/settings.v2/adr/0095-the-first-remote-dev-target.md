# 0095. The first remote-dev target

Date: 2026-09-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The mirroring model covers many cases. Remote dev needs a first concrete step. Discussed with Vyacheslav Moklev.

## Decision

**First: the frontend application layer travels to the backend and sits below the backend application layer; the
project layers travel to the frontend, every locality except environmental. The application snapshot cache stays;
the project cache is doubtful and worth a try.** One feature may be two plugins, one per side.

## Consequences

- The frontend owns a mirrored layer from the first step, so the symmetry is real.
- A copy layer needs a declared position that the holding side applies without the owner's provider.
- A key identity must stop following the plugin id, or a two-plugin feature has two identities.
- The resulting behavior is in the [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).
