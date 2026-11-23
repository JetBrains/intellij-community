# 0174. The settings engine declares its local and environmental files as ignored, and the VCS applies its own rules

Date: 2026-11-16

## Status

Accepted ([IJPL-261040](https://youtrack.jetbrains.com/issue/IJPL-261040)). Details a consequence of
[ADR 0075](./0075-each-locality-has-its-own-file.md).

## Context

The local and the environmental config files must stay out of VCS.
[ADR 0075](./0075-each-locality-has-its-own-file.md) says that the platform adds them to `.gitignore`, but it does not
say how.

The platform already has one mechanism for this. A component declares its local files through the
`com.intellij.ignoredFileProvider` extension point. The VCS subsystem then decides what to do with the declared
files: how to show them, when to write the ignore file of a VCS root, and whether to ask the user first.

## Decision

**The settings engine only declares which of its files are ignored. It registers one ignored-file provider that
names the local and the environmental config files of the project layer and of the file layer. The VCS subsystem
applies its own rules to them.**

The provider lives in the module `intellij.platform.settings.v2.vcs`. The module ships with the settings engine, in the
same module set as the settings backend. It is optional, and it loads only when the VCS module
`intellij.platform.vcs` is present. The VCS module is content of the VCS plugin. A product can leave that plugin out,
and a user can disable it. The settings backend must load without it, so the backend cannot depend on the VCS module.

## Consequences

- The declaration works for every VCS, not only for Git.
- The settings engine writes no ignore file, and it has no setting or notification of its own for it.
- An entry in the ignore file of a VCS root depends on the rules of the VCS subsystem, such as the consent of the
  user.
- For a file outside `.idea`, the VCS subsystem writes ignore entries only when the registry key
  `vcs.ignorefile.generation` is on. The key is off by default. With the default, the declaration only keeps the IDE
  from adding the files to VCS automatically. This is accepted, because the VCS subsystem owns the rule.
- A product without the VCS module declares no file. The settings engine works the same there.
- A file that VCS already tracks stays tracked. The settings engine gives no warning about it.
- The application config files are outside every project, so the provider does not name them.
- The resulting behavior is in the [layer locality spec](../spec/layer-locality.spec.md).

## Rejected alternatives

### A silent `.gitignore` inside `.jetbrains/`

The Git plugin writes `.idea/.gitignore` this way. Such a file covers only the project layer, because a directory
config can be anywhere in the tree. It also works for Git only, and it repeats a job of the VCS subsystem.

### Both the provider and the silent `.gitignore`

Two mechanisms for one job, and the second one still does not cover the directory configs.

### The provider in the settings backend

The backend would then depend on the VCS module. The backend must load also when the VCS plugin is absent or disabled.

### The provider as content of the VCS plugin

The module would then ship with the VCS plugin, not with the settings engine. The settings engine keeps its own
modules together.
