---
name: Recent Files Model Hosting
description: Requirements for the rebuild that hosts the recent files model in the shared module, so the Switcher works in a light session without a backend.
style: plain-1
targets:
  - ../plugin/resources/META-INF/plugin.xml
  - ../shared/src/com/intellij/platform/recentFiles/shared/*.kt
  - ../shared/resources/intellij.platform.recentFiles.xml
  - ../shared/intellij.platform.recentFiles.iml
  - ../backend/src/com/intellij/platform/recentFiles/backend/*.kt
  - ../backend/resources/intellij.platform.recentFiles.backend.xml
  - ../backend/intellij.platform.recentFiles.backend.iml
  - ../frontend/src/com/intellij/platform/recentFiles/frontend/model/RecentFileModelSynchronizer.kt
  - ../frontend/intellij.platform.recentFiles.frontend.iml
  - ../tests/intellij.platform.recentFiles.tests.iml
  - ../tests/testSrc/com/intellij/platform/recentFiles/backend/RecentFilesDistributedModelTest.kt
---

# Recent Files Model Hosting

Status: Draft
Date: 2026-09-21

## Summary

The recent files model feeds the Switcher popup. It keeps the list of the recent files per kind and
builds the presentation of each file. Before this change the model lived in the backend content
module. A light session has no backend, so the popup showed no history there.

This spec states how to rebuild the change that moves the model into the shared content module. The
shared module loads in every product mode. The backend module keeps only the code that needs a
backend: the path text from the file name index, the VCS status listener, and the RPC provider. One
function, `doesProcessHostRecentFilesModel()`, decides whether this process hosts the model.

The first attempt lives on the branch `khbminus/light-2/monolith-product-mode`, in the four
`IJPL-252054` commits and the working tree. It created a fourth content module,
`intellij.platform.recentFiles.model`. The rebuild does not repeat that. It keeps the guard function,
the controller entry points, the lite RPC resolution, and the frontend restart from that attempt.

[Recent Files](recent-files.spec.md) states the behavior of the popup and of the list. This spec
states the placement only. The IJPL UI group owns the plugin.

## Goals

- Show the recent files history in the popups of a `LIGHT` session.
- Keep the behavior of the monolith, the frontend, and the backend modes unchanged.
- Keep every backend-only dependency in the backend module.
- Keep the host decision in one function with few callers.

## Non-goals

- A change of the popup, of the actions, or of the RPC contract.
- A fourth content module. The shared module holds the model.
- The product mode transition itself. The platform owns it.
- The fallback switcher behind the `switcher.use.fallback.in.monolith` registry key.

## Requirements

### Plugin layout

- The plugin `intellij.recentFiles.plugin` must ship three content modules: `shared`, `backend`, and `frontend`.
- The module names stay `intellij.platform.recentFiles`, `intellij.platform.recentFiles.backend`,
  and `intellij.platform.recentFiles.frontend`.
- The plugin must not declare a module named `intellij.platform.recentFiles.model`.
- The `backend` module must carry `required-if-available="intellij.platform.backend"` in the plugin descriptor.
- The `shared` module and the `frontend` module must load in every product mode.
- No module of the plugin may depend on `intellij.platform.rpc`. A light session blocks that module.
- The `shared` module must depend on `intellij.platform.rpc.lite` for the remote API resolution.
- The `frontend` module must depend on `fleet.rpc` for `durable`, and on `intellij.platform.core.impl`
  and `intellij.platform.productMode`.
- The `backend` module must depend on `intellij.platform.backend` with the `RUNTIME` scope only.

### The shared module

The `shared` module holds the RPC contract, as before, and now the model. Every class stays in the
package `com.intellij.platform.recentFiles.shared`.

| Class | Role |
| --- | --- |
| `RecentFileEventsModel` | The project service with one event flow per kind. It builds the presentation and debounces the updates. |
| `RecentFilesModel` | The project service with the file list per kind. The event model reads it back. |
| `RecentFilesModelMutableState` | The `VirtualFile` state behind `RecentFilesModel`. |
| `RecentFileEventsController` | The entry point of a file event. It holds the guard. |
| `RecentFileEventsModelSynchronizer` | The startup activity that feeds `RecentFilesModel` from the event model. |
| `FileSwitcherApiImpl` | The application service behind `FileSwitcherApi`. |
| `RecentFilesVfsListener` | The asynchronous VFS listener for a removal, a rename, a move, and a content change. |
| `ChangedIdeHistoryFileHistoryOrderListener` | The editor history order listener. |
| `RecentFilesDaemonAnalyserListener` | The daemon listener. |
| `RecentFilesProblemsListener` | The problem listener. |
| `RecentFilePresentationContributor` | The extension point for the path text. |
| `recentFilesCollector.kt` | `getFilesToShow` and `createRecentFileViewModel`. |

- Every class in the table must be `internal`, except the controller and the extension point interface.
- The controller and the extension point interface must be public and carry `@ApiStatus.Internal`.
- The in-process event type must be `LocalRecentFilesEvent`, and the presentation type `LocalRecentFilePresentation`.
- No class of the `shared` module may carry the `Backend` prefix.
- The model must compute the problem mark with `WolfTheProblemSolver` directly.
- The model must schedule the rehighlighting with `HighlightingPassesCache` directly.
- The model must ask every `recentFiles.presentationContributor` extension for the path text.
  The first non-null answer wins. The text is empty when no extension answers.
- The `shared` module must declare the registry key `switcher.presentation.update.debounce.interval.ms`.
- The api dump of the `shared` module must stay empty.

### The backend module

- The `backend` module must hold exactly three classes: the path text contributor, the VCS status
  listener, and the RPC provider.
- `BackendRecentFilePathContributor` must implement `RecentFilePresentationContributor`.
- The contributor must return null in dumb mode.
- The contributor must return null when no other file of the search scope has the same name.
- The contributor must use the project scope, and the all scope in Rider.
- The contributor must cache the same-name answer in the user data of the file.
- The contributor must return the directory relative to the project directory when the file is inside it.
- The contributor must otherwise return the directory relative to the home directory of the user.
- The contributor must return the full directory when neither rule applies.
- `RecentFilesVcsStatusListener` must report one changed file through `presentationChanged`.
- `RecentFilesVcsStatusListener` must report a VCS-wide change through `allPresentationsChanged`.
- `RecentFilesBackendApiProvider` must serve the application service behind `FileSwitcherApi` over RPC.
- The provider must not construct a second implementation of the contract.

### The host of the model

Exactly one process of a session hosts the model:

| Product mode | Hosts the model | Shows the popup |
| --- | --- | --- |
| `MONOLITH` | yes | yes |
| `BACKEND` | yes | no |
| `LANGUAGE_SERVER` | yes | no |
| `FRONTEND` | no | yes |
| `LIGHT` | yes | yes |
| `LIGHT_WITH_RD_CONNECTION` | no | yes |

- `doesProcessHostRecentFilesModel()` must be the single function that holds this rule.
- The function must return false when the fallback switcher key is on.
- The function must return true when the mode is `LIGHT`.
- The function must return true when the mode is not a frontend process.
- The function must return false in every other case.
- The function must be `internal` and live in the `shared` module, in the file of the controller.
- The rule must match `awaitWithLocalFallback`. A `LIGHT` session serves itself.
  A session with a connection awaits its backend.

### The callers of the guard

- The function must have exactly three callers.
- The controller must call it once, in the one private path that every entry point uses.
- The startup activity must call it once and return early when the answer is false.
- The VFS listener must call it once and return null when the answer is false.
- No other class may call the function or read the product mode.
- The `frontend` module must not read the product mode to choose the model. It calls `FileSwitcherApi.getInstance()`.
- Each descriptor entry of a guarded class must carry the comment `see doesProcessHostRecentFilesModel`.

### The event entry points

- The controller must expose `presentationChanged(project, files)` for a file whose presentation can have changed.
- The controller must expose `allPresentationsChanged(project)` for a change that touches every file.
- The controller must expose `filesAdded(project, files)` as `internal`, for the history order listener.
- The controller must expose `filesRemoved(project, files)` as `internal`, for the VFS listener.
- The controller must drop a directory.
- The controller must drop the event before it touches a model service when the guard answers false.
- `allPresentationsChanged` must read the model only after the guard answered true.
- The controller must pass the accepted files to the event model with the matching `FileChangeKind`.

### The listeners

- The daemon listener and the problem listener must be declarative project listeners with `activeInTestMode="false"`.
- The daemon listener must report the files of the finished editors through `presentationChanged`.
- The problem listener must report an appeared, changed, or disappeared problem through `presentationChanged`.
- The VFS listener must be a `vfs.asyncListener` with the id `RecentFileRemovalListener`.
- The VFS listener must return null in unit test mode.
- The VFS listener must report a deletion through `filesRemoved`.
- The VFS listener must report a rename, a move, or a content change through `presentationChanged`.
- The VFS listener must report only a valid file that the project file index holds as content.
- The descriptor entry of the VFS listener must carry `<!--suppress SplitModeXmlApiUsage -->`,
  and the class `@Suppress("SplitModeApiUsage")`.
- The event model must subscribe the history order listener itself, outside unit test mode, for a `ProjectEx` project.
- The startup activity must subscribe `RecentFilesModel` to the three kinds.

### The model contract resolution

- `FileSwitcherApi.getInstance()` must resolve through `LiteRemoteApiProviderService.awaitWithLocalFallback`.
- The local fallback must be `service<FileSwitcherApi>()`, so the contract does not name the implementation class.
- The call must return the local service in a `LIGHT` session.
- The call must await the backend connection and return the remote API in every other mode.
- The `shared` module must register `FileSwitcherApiImpl` as the application service behind `FileSwitcherApi`.
- The RPC provider of the `backend` module and the local fallback must serve the same service instance.

### The frontend restart

- The frontend synchronizer must restart its subscriptions when the product mode of the applied plugin set changes.
- The source is `PluginManagerCore.currentInitContextFlow`, mapped to the product mode and made distinct.
- The restart must cancel the previous subscriptions and fetches first.
- The restart must resolve `FileSwitcherApi` again, so the backend takes the model over after a light upgrade.
- The synchronizer must return early when the fallback switcher key is on.

### The descriptors

The `shared` descriptor `intellij.platform.recentFiles.xml` declares:

- the extension point `com.intellij.recentFiles.presentationContributor`, dynamic;
- the registry key `switcher.presentation.update.debounce.interval.ms`, default 300;
- the application service behind `FileSwitcherApi`;
- the VFS listener and the startup activity;
- the daemon listener and the problem listener as project listeners.

The `backend` descriptor `intellij.platform.recentFiles.backend.xml` declares:

- the `platform.rpc.backend.remoteApiProvider` for `FileSwitcherApi`;
- the `recentFiles.presentationContributor` for the path text;
- the VCS status listener as a project listener.

The `frontend` descriptor keeps its content. Only the generated dependency list changes.

### The build files

- The `.iml` files are the source of truth. Run `./build/jpsModelToBazel.cmd` after every `.iml` or descriptor change.
- The `shared` `.iml` must list every module the compiler needs and no more.
  The model adds at least `intellij.platform.productMode`, `intellij.platform.ide.core`,
  `intellij.platform.projectModel`, `intellij.platform.editor.ui`, and `intellij.platform.util.coroutines`.
- The `backend` `.iml` must drop every dependency that only the moved classes used.
- The tests `.iml` must set `production-module="intellij.platform.recentFiles"`, so the test reads the `internal` model.
- The tests `BUILD.bazel` must list the `shared` targets as `associates`.
- `community/build/bazel-generated-file-list.txt` must not list `platform/recentFiles/model`.
- Neither `modules.xml` may list a model module.

## User Experience

- The popups show no change in the monolith, the frontend, and the backend modes.
- A `LIGHT` session shows the history, the problem mark, the icon, and the colours of each file.
- A `LIGHT` session shows an empty path text, because it has no file name index.
- After the upgrade to a backend, the path text appears once the backend model answers.

## Data & Backend

- The RPC contract stays as it is: `FileSwitcherApi`, the four requests, and the four events.
- The local service and the RPC provider serve the same requests with the same answers.
- The `Local` types stay inside the process. The RPC types cross the connection.

## Error Handling

- The model must answer false to a request when it cannot resolve the project.
- A dropped connection must not stop the frontend. `durable` subscribes again.
- A plugin set change during a fetch must cancel the fetch and start the fetch again.
- The VFS listener and the daemon listener must do nothing in a unit test.

## Testing / Local Run

- The distributed model test drives the frontend model and the shared model in one process.
  [@test] ../tests/testSrc/com/intellij/platform/recentFiles/backend/RecentFilesDistributedModelTest.kt
- The light check needs a real run. Start the `JetBrains Light (Light, All Plugins)` run
  configuration, open a directory, open two files, and press `Ctrl+E`. The popup must list both
  files, and a file with an error must show the problem mark.
- Then upgrade the session to a backend and open the popup again. The list must survive, and the
  path text must appear for a duplicate name.
- Build the three modules from the root after each step:

```bash
./bazel.cmd build @community//platform/recentFiles/...
```

## Rebuild Steps

Two commits rebuild the change. Each commit compiles and passes the distributed model test.

1. `IJPL-252054 [recentFiles] move the recent files model to the shared module`
   - Move the model classes from `backend` to `shared`, with the `Local` prefix and without the `Backend` prefix.
   - Add the guard function, the controller entry points, and the extension point for the path text.
   - Keep the path contributor, the VCS listener, and the RPC provider in `backend`.
     The provider serves the application service.
   - Move the registry key, the VFS listener, the startup activity, the daemon listener, and the
     problem listener to the `shared` descriptor.
   - Set `required-if-available` on the `backend` module. Move the backend dependency to the `RUNTIME` scope.
   - Point the tests module at `shared`. Run `./build/jpsModelToBazel.cmd`.
2. `IJPL-252054 [recentFiles] resolve FileSwitcherApi through the lite RPC service`
   - Replace `intellij.platform.rpc` with `intellij.platform.rpc.lite` in `shared`, and with `fleet.rpc` in `frontend`.
   - Resolve `FileSwitcherApi.getInstance()` through `awaitWithLocalFallback` with the service fallback.
   - Restart the frontend synchronizer on a product mode change.
   - Update `.agents/skills/ij-light/references/dev-workflow.md`: the frontend module no longer
     blocks `intellij.performanceTesting.frontend`.
   - Run `./build/jpsModelToBazel.cmd`.

## Open Questions / Risks

- The extension point keeps the name `recentFiles.presentationContributor` with one method.
  A rename to a path text name is possible.
- The VFS listener keeps only a file that the project file index holds as content. Verify that a
  deleted file leaves the list in a `LIGHT` session, because a light project may have no content roots.
- The distributed model test runs in the monolith mode. No automatic test covers the `LIGHT`
  resolution or the restart on the upgrade.
- The history order listener stays a programmatic subscription because of a test-mode workaround
  in the platform. A declarative listener would remove the `ProjectEx` check.

## References

- [Spec format](../../../.ai/spec/SPEC_GUIDE.md)
- [Recent Files](recent-files.spec.md)
- `community/platform/platform-impl/rpc/src/com/intellij/ide/rpc/LiteRemoteApiWithLocalFallback.kt`
- `community/platform/productMode/src/ProductMode.kt`
- The ij-light skill: `.agents/skills/ij-light/SKILL.md`
