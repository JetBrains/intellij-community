# 0152. A config file is created on its first write

Date: 2026-10-09

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The application layer and the project layer create their config files when they start. So every installation and
every opened project gets empty settings files, even when no setting is ever written. The file layer creates a
directory config only on its first write.

## Decision

**Every layer creates a config file only on its first write. No layer creates an empty file when it starts.** A
missing file reads as an empty layer.

## Consequences

- An installation or a project that writes no setting gets no settings file.
- The watcher of a layer must notice a file that appears after the start, for example one that a user creates by
  hand or that a VCS checkout brings.
- The application, project and file layers behave the same way.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).
