---
name: File Layers
description: Requirements for the built-in JSON file layers of the application, the project, directories and files.
style: plain-1
targets:
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/AppDefaultsLayerProvider.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/AppDefaultsLayerService.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/AppLayerProvider.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/AppLayerService.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/ProjectLayerProvider.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/ProjectLayerService.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/BaseLayerProvider.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/BaseLayerService.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/JsonConfigFile.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/ConfigFileWatcher.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/localityFiles.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsFileCodec.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsValueJson.kt
  - ../backend/src/com/intellij/platform/settings/v2/impl/providers/FileLayerService.kt
  - ../backend/src/com/intellij/platform/settings/v2/impl/providers/JsonFileLayerProvider.kt
---

# File Layers

Status: Draft
Date: 2026-09-28

## Summary

The platform ships four layers, each stored in human-editable JSON files. The application defaults are read-only. The
application layer holds the settings of one installation. The project layer holds the settings of one project. The
file layer holds the settings of a directory and of each file in it, in one config file inside the directory.

## Goals

- Keep settings in files that a user can read and edit by hand.
- Keep directory settings with the directory, so a rename or a move under VCS carries them along.
- Pick up an external edit of a config file while the IDE runs.

## Non-goals

- The resolution order of these layers. The [Layer Resolution](layer-resolution.spec.md) spec owns it.

## Requirements

### Layers and files

- The application defaults layer must be first and read-only, from the `settings/app.settings.default.json`
  resource of the platform. It offers no locality for a write at any context.
  See [ADR 0017](../adr/0017-a-context-dependent-default-is-a-factory-and-a-data-driven-default-is-a-layer.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerWriteLocalitiesTest.kt
- The application defaults layer must read its defaults only through the class loader. The defaults ship inside
  the IDE, and no defaults file lives in the config directory.
  See [ADR 0147](../adr/0147-the-application-defaults-ship-with-the-ide-and-are-only-read.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerWriteLocalitiesTest.kt
- The application layer must store in `app.settings.json` and its locality siblings, in the config `options`
  directory. It takes writes at the application context only.
  See [ADR 0075](../adr/0075-each-locality-has-its-own-file.md).
- The application layer must have only the team and the environmental locality, with `app.settings.json` and
  `app.settings.env.json`. It must not read, create, watch or write an `app.settings.local.json`. So a plain write
  of a shareable key at the application context lands in `app.settings.json`.
  See [ADR 0149](../adr/0149-the-application-layer-has-no-local-locality.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerWriteLocalitiesTest.kt
- The project layer must store in `<basePath>/.jetbrains/project.settings.json` and its locality siblings. It takes
  writes at the project context only. See [ADR 0157](../adr/0157-the-project-layer-stores-its-files-in-the-jetbrains-directory-under-the-base-path.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsConfigFileCreationTest.kt
- A save of the project layer for a project with no base path must be dropped, with a warning that holds the
  dropped settings as JSON. See [ADR 0122](../adr/0122-a-save-to-a-storage-context-that-does-not-exist-is-dropped.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNotOpenProjectTest.kt
- The project layer must offer no locality at the context of a project that is not open, such as the default
  project. See [ADR 0129](../adr/0129-a-layer-filters-keys-and-offers-localities-per-context.md). The vault drops a
  mutation there before it asks the layer, as [Settings Writes](settings-writes.spec.md) says.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNotOpenProjectTest.kt
- The application and defaults layers must load only for the application scope. The project layer must load every
  open project for the application scope, only its project for a project scope, and nothing for a narrower scope.
  The file layer must load the files of every open project for the application scope, and of one project otherwise.
- The file layer must store a directory in `.jetbrains.json` and its locality siblings inside that directory.
  See [ADR 0022](../adr/0022-directory-settings-live-in-a-file-inside-the-directory.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/FileLayerProviderTest.kt
- The file layer must take writes at file and directory contexts only.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerWriteLocalitiesTest.kt
- The file layer must find on disk whether a context is a file or a directory. A path that does not exist is its
  own storage context. See [ADR 0121](../adr/0121-a-file-context-has-no-directory-flag.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/PerFileLayerTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/FileLayerProviderTest.kt
- A save to a storage context that is not a directory on disk must be dropped. The file layer logs a warning with
  the dropped settings as JSON, and it does not create the directory. See [ADR 0122](../adr/0122-a-save-to-a-storage-context-that-does-not-exist-is-dropped.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/FileLayerProviderTest.kt
- A project load must find every directory that holds a config of any locality: team, local or environmental.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFileLocalityTest.kt
- The file layer must find the directory configs by their file names in the file name index. It must wait until
  the index is ready, and it must not walk the project content. See [ADR 0163](../adr/0163-the-file-layer-finds-the-directory-configs-through-the-file-name-index.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/FileLayerIndexLookupTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFileLocalityTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadSweepTest.kt
- A reload of the project or the application scope in dumb mode must make the application and the project contexts
  ready. A directory context must stay dirty until smart mode returns, and then its config must load.
  [@test] ../test/src/com/intellij/platform/settings/v2/FileLayerIndexLookupTest.kt
- The wait for the index must block no other startup activity and no indexing. The platform runs the load of a
  project open in its own coroutine, and indexing does not wait for it.
- The file layer must find only the configs in the project content. A config in an excluded directory never loads.
  [@test] ../test/src/com/intellij/platform/settings/v2/FileLayerIndexLookupTest.kt
- A project that closes while the file layer waits for its index must end the wait, and it must give no configs.
  The platform drops a wait for smart mode when the project closes and never resumes it.
  [@test] ../test/src/com/intellij/platform/settings/v2/FileLayerIndexLookupTest.kt
- Every layer must create a config file only on its first write. No layer creates an empty file when it starts.
  The application layer and the project layer also create a missing directory of the file. The file layer creates
  a config file only in an existing directory.
  See [ADR 0152](../adr/0152-a-config-file-is-created-on-its-first-write.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsConfigFileCreationTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerWriteLocalitiesTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFileLocalityTest.kt
- A missing config file must read as an empty layer.
  See [ADR 0152](../adr/0152-a-config-file-is-created-on-its-first-write.md).
- The watcher of the application layer and of the project layer must pick up a config file that appears after the
  start. This includes a file that comes together with a new directory, such as a new `.jetbrains` directory.
  See [ADR 0152](../adr/0152-a-config-file-is-created-on-its-first-write.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsConfigFileCreationTest.kt

### Per-file settings

- A file context must store in the config of its directory, under `@@perfile`, keyed by the file name.
  See [ADR 0023](../adr/0023-file-level-settings-nest-in-the-directorys-config.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/PerFileLayerTest.kt
- A file value must override the directory for that file only. Its siblings inherit the directory value.
  [@test] ../test/src/com/intellij/platform/settings/v2/PerFileLayerTest.kt
- Two per-file entries must coexist, and a change of one must keep the other.
  [@test] ../test/src/com/intellij/platform/settings/v2/PerFileLayerTest.kt
- A per-file entry for a file that the directory does not hold must be ignored. A per-file entry whose name is a
  subdirectory must also be ignored. See [ADR 0121](../adr/0121-a-file-context-has-no-directory-flag.md).
- A save must write the per-file entries from the vault only. A per-file entry that the load ignores must not
  stay in the config.
  [@test] ../test/src/com/intellij/platform/settings/v2/PerFileLayerTest.kt
- A per-file entry that disappears from the config must clear that file's layer.
- A rename or a move of a file must carry its per-file entry along. A move puts the entry into the config of the
  new directory. See [ADR 0130](../adr/0130-per-file-settings-follow-their-file-on-a-rename-or-a-move.md).

### Format

- A config file must be a plain JSON object of `key identity: value`, with no type tags.
  See [ADR 0001](../adr/0001-json-is-the-default-settings-format.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/FileLayerProviderTest.kt
- Nested maps and lists must render as JSON objects and arrays.
- A JSON number must read as the smallest of int, long and double that holds it.
- A blank or absent file must read as an empty layer.
- A config whose top level is not a JSON object must count as malformed.
  See [ADR 0145](../adr/0145-a-reload-treats-a-malformed-config-as-missing.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsJsonRobustnessTest.kt
- Values of keys that are not loaded must stay in the file.

### Changes on disk

- The application, project and file layers must watch their files and reload a file after an external change.
- A push of a malformed file must keep the previous layer. A reload must drop the layer of a malformed file, as
  for a missing file. The next save then overwrites the file. See [ADR 0145](../adr/0145-a-reload-treats-a-malformed-config-as-missing.md).
- Deleting a directory config must clear the directory layer and every per-file layer it held.
  [@test] ../test/src/com/intellij/platform/settings/v2/PerFileLayerTest.kt
- The file layer must write through the file system, not the VFS. An editor with unsaved changes then sees a
  conflict.

## Error Handling

- A config that cannot be parsed is logged at debug level and skipped.
- A directory config that fails to load does not stop the other configs from loading.

## Open Questions / Risks

- The directory configs load only after indexing, also after a branch switch that starts indexing. If the file
  name index can answer before indexing ends, the configs could load earlier. This is not checked yet.
- While a project indexes, the file bucket of a reload holds the file reload mutex. A second reload then holds the
  project mutex while it waits for the file mutex. So a third reload installs its project bucket only after the
  indexing. The wait climbs one kind for each queued reload. The third reload takes the mutexes hand over hand, so it
  holds the application mutex while it waits for the project mutex. A fourth reload then waits for the application
  mutex, and all of its kinds stay pending. So every context under the scope of the fourth reload stays dirty. For
  the application scope, every write to the application context then waits for the indexing. See
  [ADR 0162](../adr/0162-reloads-take-one-mutex-per-context-kind-from-the-application-down.md) and
  [ADR 0115](../adr/0115-a-write-waits-only-for-a-reload-of-its-own-context-or-an-ancestor.md).
- A write of an environmental file inside the project tree is visible on a remote-dev frontend, where it does nothing.
  See [ADR 0097](../adr/0097-an-environmental-key-and-its-layer-stay-on-one-side.md).
- Deleting the application or project file is not covered by a test.
- The watcher that moves a per-file entry on a rename or a move is not built. Until it is, a renamed or moved file
  loses its settings. The entry of a deleted, renamed or moved file stays until the next load of its directory
  config, for example at the next start, and a new file with the same name gets those settings until then. See [ADR 0130](../adr/0130-per-file-settings-follow-their-file-on-a-rename-or-a-move.md).
