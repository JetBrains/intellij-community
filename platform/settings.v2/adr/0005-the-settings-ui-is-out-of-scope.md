# 0005. The settings UI is out of scope

Date: 2026-05-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The settings dialog is a known pain, and a layered model invites a layer-aware UI: show where a value comes
from, whether it is overridden, and let the user pick the layer to edit. Non-modal settings (IJPL-715) and a
reorganized dialog are already separate efforts.

## Decision

**This task is the engine and the API: resolving, storing and writing values. Designing the settings UI is
not part of it.** The API must make a layer-aware UI possible, and no more.

## Consequences

- The engine can ship without waiting for UI design.
- Capabilities meant for a UI (per-layer contributions, validation messages) are built as engine features;
  the UI that uses them is someone else's work.
