# 0157. The project layer stores its files in the `.jetbrains` directory under the base path

Date: 2026-10-19

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The project layer stored `project.settings.json` and its locality files in `.idea`. No ADR recorded that place.

## Decision

**The project layer stores `project.settings.json` and its locality files in the `.jetbrains` directory under the
base path of the project, not in `.idea`.**

## Consequences

- The config of a project is at `<basePath>/.jetbrains/project.settings.json`, and the local and environmental files
  sit next to it.
- The directory is created on the first write, like the files
  ([ADR 0152](./0152-a-config-file-is-created-on-its-first-write.md)).
- The directory shares its name with the directory configs
  ([ADR 0075](./0075-each-locality-has-its-own-file.md)).
- No file in `.idea` is read. Nothing is migrated, because no user has settings v2 files yet.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).
