# 0173. The team-editing mode is toggled through two Go to Action options of the backend baseline module

Date: 2026-11-16

## Status

Accepted ([IJPL-261039](https://youtrack.jetbrains.com/issue/IJPL-261039)).

## Context

The team-editing mode is the environmental key `EDIT_TEAM_LAYER`, valid at the application and at the project context
([ADR 0076](./0076-settings-have-three-localities-a-write-goes-to-local-by-default-and-team-editing-is-a-per-machine-mode.md)).
A user could turn it on only by an edit of the environmental config file.

- No settings page holds an option of the engine itself.
- The engine has no bridge to the storage of the older settings
  ([ADR 0135](./0135-settings-v2-has-no-settings-controller-bridge.md)).
- The mode is a rare job, for example for devops. Low discoverability is acceptable.
- Each remote-dev side has its own vault ([ADR 0084](./0084-each-side-has-its-own-vault-and-every-read-is-local.md)),
  and an environmental key stays on one side
  ([ADR 0097](./0097-an-environmental-key-and-its-layer-stay-on-one-side.md)).
- A write suspends ([ADR 0032](./0032-reads-are-synchronous-and-writes-suspend.md)), and an option gets its new state
  through a plain call on the EDT.

## Decision

**Two boolean options of Go to Action and Search Everywhere toggle the mode: one at the application context and one
at the context of the current project. The backend baseline module registers them.**

- The options come from options top hit providers. An option has a getter and a setter of its own, so it reads and
  writes the key directly. It has no settings page.
- The project option exists only with a project. It shows the value that the project context resolves.
- The option launches its write in a coroutine and reports the requested state until the write ends.

## Consequences

- A user finds the option only by the words of its label.
- In remote development the options control the backend, the side that owns the project files. The frontend has no
  options.
- The project option is on when only the application mode is on. Turning it off writes an explicit `false` at the
  project.
- A write that fails is logged, and the option then shows the stored value again. The user gets no other notice.
- The resulting behavior is in the [layer locality spec](../spec/layer-locality.spec.md).

## Rejected alternatives

### An advanced setting

An advanced setting is an application value with a storage of its own. The key lives in the vault, and at two
levels. It would need a bridge between the two storages.

### Options in the shared module

The shared module loads on both remote-dev sides. The search would then show the options of both sides with the same
labels, and each pair would control another vault.

### A blocking write

A blocking write on the EDT needs a modal progress. The new Search Everywhere popup flips its switch by itself, and
the old one reads the state right after the call, so the requested state covers both without a block.
