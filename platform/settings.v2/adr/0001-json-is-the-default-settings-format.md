# 0001. JSON is the default settings format

Date: 2026-01-20

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0003](./0003-the-settings-api-is-format-neutral.md).

## Context

Settings are meant to become human-readable and editable, like code: a user opens the file in vim and changes
a value. The two candidates were JSON and YAML. JSON is less friendly to read and write by hand. YAML is
friendlier, but it depends on indentation, which makes hand edits fragile.

## Decision

**Settings files are JSON by default, and the platform may also parse YAML.**

## Consequences

- Files are strict and simple to parse, and every tool understands them.
- Hand editing is a bit noisier than YAML would be.
- A YAML reader stays possible, so a team that prefers YAML is not locked out.
- The naming convention for keys in the files is still open.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### YAML as the default

Indentation-sensitive, so a hand edit breaks structure easily.
