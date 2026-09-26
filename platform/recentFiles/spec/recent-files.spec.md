---
name: Recent Files
description: Requirements for the recent files model, the Switcher popup, and the behavior of both in the monolith, split and light product modes.
style: plain-1
targets:
  - ../plugin/resources/META-INF/plugin.xml
  - ../shared/src/com/intellij/platform/recentFiles/shared/*.kt
  - ../shared/resources/intellij.platform.recentFiles.xml
  - ../backend/src/com/intellij/platform/recentFiles/backend/*.kt
  - ../backend/resources/intellij.platform.recentFiles.backend.xml
  - ../frontend/src/com/intellij/platform/recentFiles/frontend/*.kt
  - ../frontend/src/com/intellij/platform/recentFiles/frontend/model/*.kt
  - ../frontend/resources/intellij.platform.recentFiles.frontend.xml
---

# Recent Files

Status: Draft
Date: 2026-09-21

## Summary

Recent Files keeps the list of the files that the user opened or changed in a project. A popup shows
the list, and the user opens a file from it. The `Switcher` action opens the quick popup with
`Ctrl+Tab`. The `Recent Files` action opens the full popup with `Ctrl+E`.

The feature has two parts. The model reads the editor history of the project and builds the
presentation of each file. The user interface shows the popup and sends the user commands back. The
two parts can run in different processes, and one contract connects them.

The product mode of the process decides the placement. A monolith session runs both parts in one
process. A remote development session runs the model on the backend and the user interface on the
frontend. A light session runs both parts in one process, but the process has no file name index.

This spec states the behavior of the list, of the popup, and of the placement in each product mode.
[Recent Files Model Hosting](recent-files-model-hosting.spec.md) states which content module holds
each class. The IJPL UI group owns the plugin and this spec.

## Goals

- State what each popup shows and how the user acts on it.
- State which process hosts the recent files model in each product mode.
- State which part of the presentation needs a backend, and what a light session shows instead.
- Keep one behavior in every product mode, except where this spec allows a difference.

## Non-goals

- The editor history itself. `EditorHistoryManager` and `IdeDocumentHistory` own it.
- The old popup behind the `switcher.use.fallback.in.monolith` registry key. It is a fallback only.
- The transition of a running process from one product mode to another. The platform owns it.
- Recent Locations, Search Everywhere, and the editor tab list. They read the same editor history.

## Requirements

### Plugin layout

- The plugin `intellij.recentFiles.plugin` must ship three content modules.
  The names are `shared`, `backend` and `frontend`.
- The `shared` module must hold the model contract and the model.
- The `backend` module must load only when the process provides `intellij.platform.backend`.
  The plugin declares this rule with `required-if-available`.
- The other two modules must load in every product mode.
- No module may depend on `intellij.platform.rpc`, because a light session blocks that module.
  The `shared` module depends on `intellij.platform.rpc.lite` instead.

### Product modes

Exactly one process of a session hosts the model. The product mode decides which one:

| Product mode | Hosts the model | Shows the popup |
| --- | --- | --- |
| `MONOLITH` | yes | yes |
| `BACKEND` | yes | no |
| `LANGUAGE_SERVER` | yes | no |
| `FRONTEND` | no | yes |
| `LIGHT` | yes | yes |
| `LIGHT_WITH_RD_CONNECTION` | no | yes |

- A process must host the model when the mode is `LIGHT`, or when the mode is not a frontend process.
- A monolith process must use the old popup when `switcher.use.fallback.in.monolith` is on.
  A JetBrains Client and a remote development host must always use this plugin.
- No process must host the model while the old popup is in use.
- The user interface must resolve the model contract through the lite remote API service.
- The service must return the local model when the product mode is `LIGHT`.
- The service must wait for the remote model in every other product mode.
  [@test] ../../../../remote-dev/rdct-tests/rdct-tests-distributed/src/com/jetbrains/rdct/common/distributed/platform/ui/RecentEditsTest.kt
- Both parts must restart their work when the product mode of the process changes.
  A light session that gains a backend must move the model to the backend.

### The recent files kinds

- The model must keep three kinds of list, and each kind must have its own state.
- The `RECENTLY_EDITED` kind holds the changed files of the document history.
- The `RECENTLY_OPENED` kind holds the editor history and the open files.
- The `RECENTLY_OPENED_UNPINNED` kind holds the short list for the quick popup.
- A file must appear in a list one time only.
- A directory must never appear in a list.

Rules for the `RECENTLY_OPENED` kind:

- The model must put an open file that the editor history does not hold into the list.
  The file goes in front of the first open file of the history.

Rules for the `RECENTLY_OPENED_UNPINNED` kind:

- The model must use the editor selection history of the user interface when it holds two files or more.
- The model must otherwise cut the editor history down to the number of visible tool windows.
  The two columns of the quick popup then have a similar height.
- The user interface must read the `RECENTLY_OPENED` kind when the unpinned kind holds one file or none.
  It must keep the unpinned kind when that one file has several open editors.

### Membership rules

- A file enters a list only when the file is valid.
- A file that declares itself optional enters a list only when it accepts the project.
  `IdeDocumentHistoryImpl.OptionallyIncluded` declares this.
- A `recentFiles.excluder` extension can keep a file out of the edited kind, of the opened kinds, or of both.
  [@test] ../tests/testSrc/com/intellij/platform/recentFiles/backend/RecentFilesDistributedModelTest.kt
- The model and the user interface must apply the same membership rules.

### Order and size

- The model must put a new file on the top of the list.
- An update must keep the position of the file, except when the request asks for the top.
- The model must keep a buffer per kind. The size comes from the editor history stack size.
- The buffer size must stay between 100 and 1000 files.
- The user interface must send at most 30 files of its editor selection history.
- The model must build at most 30 files for the unpinned kind.

### Presentation of a file

The model builds the presentation and sends it to the user interface:

| Field | Source |
| --- | --- |
| Main text | The custom editor tab title, or the name of the file |
| Status text | The directory of the file, relative to the home directory of the user |
| Path text | The `recentFiles.presentationContributor` extensions |
| Problem mark | `WolfTheProblemSolver` of the project |
| Icon | The icon of the file |
| Foreground colour | The colour of the file status |
| Background colour | The editor tab background colour of the file |

- A `recentFiles.presentationContributor` extension may supply the path text. The first non-null answer wins.
- The model must build a presentation when no extension supplies the path text. The path text is then empty.
- The model must compute the problem mark itself. It needs the daemon only, and every product ships the daemon.
- The `backend` module must register the extension for the path text.
  It needs the file name index, and a light session has no index.

Rules for the path text:

- The path text must appear only when the project holds another file with the same name.
- The path text must be empty while the project is in dumb mode.
- The path text must show the directory relative to the project directory when the file is inside it.
- The path text must otherwise show the directory relative to the home directory of the user.
- The path text must show the full directory when neither rule applies.

### Change propagation

- The model must send an event for every change of a list. The event names the kind and the files.
  [@test] ../tests/testSrc/com/intellij/platform/recentFiles/backend/RecentFilesDistributedModelTest.kt
- The model must send `AllItemsRemoved` before it sends the first list of a kind.
- The model must update a file that it already holds. It must ignore any other file.
- The model must remove a file from every kind when the file leaves the virtual file system.
- The model must update the presentation after a rename, a move, or a change of the content.
- The model must update the presentation after the daemon finishes the analysis of a file.
- The model must update the presentation after a problem change and after a file status change.
- The model must collect the presentation updates of a period and send them as one batch.
  The `switcher.presentation.update.debounce.interval.ms` registry key sets the period.
- The period must stay between 0 and 10000 milliseconds.
- The model must drop the oldest event when a consumer is too slow. No event may block a producer.

The user interface reports its own events to the model:

- The user interface must add an open file to the opened kind and to the unpinned kind.
- The user interface must move a selected file to the top of both opened kinds.
- The user interface must remove a closed file from the unpinned kind, when no editor shows it.
- The user interface must keep a closed file in the opened kind, unless a membership rule rejects it.
  [@test] ../tests/testSrc/com/intellij/platform/recentFiles/backend/RecentFilesDistributedModelTest.kt
- The user interface must add a file that the user types in to the edited kind.
  It must wait for a quiet period, so one burst of typing sends one file.
- The `switcher.typing.debounce.interval.ms` registry key sets the quiet period.
  The user interface reads the key one time, at the start of the process.

### Rehighlighting

- The user interface may ask the model to rehighlight the files of the list.
- The model must rehighlight only the files that no editor shows.
- The model must schedule the rehighlighting with `HighlightingPassesCache` directly. No extension takes part.

## User Experience

The feature has two popups. The quick popup carries the title `Switcher`. The full popup carries the
title `Recent Files`. The user interface keeps one popup per project and closes the other popups first.

### The actions

| Action | Default shortcut | Opens |
| --- | --- | --- |
| `Switcher` | `Ctrl+Tab` and `Ctrl+Shift+Tab` | The quick popup |
| `SwitcherForward` and `SwitcherBackward` | none | The quick popup, and it moves the selection |
| `RecentFiles` | `Ctrl+E` | The full popup, with the checkbox off |
| `RecentChangedFiles` | none | The full popup, with the checkbox on |
| `SwitcherIterateItems` | `Ctrl+E` | Nothing. It moves the selection in an open popup |
| `SwitcherRecentEditedChangedToggleCheckBox` | `Ctrl+E` | Nothing. It flips the checkbox |
| `SwitcherNextProblem` | The shortcut of `GotoNextError` | Nothing. It selects the next file with a problem |
| `SwitcherPreviousProblem` | The shortcut of `GotoPreviousError` | Nothing. It selects the previous one |
| `DeleteRecentFiles` | `Delete` | Nothing. It carries the shortcut of the delete command |

- The default keymap must bind the shortcuts of the table, and no shortcut may clash.
  [@test] ../../testFramework/extensions/src/com/intellij/keymap/KeymapsTestCase.java
- Every action must run on the frontend process. No action may go to the backend.
- Every action must stay hidden while the old popup is in use.
- The quick popup must stay disabled while a screen reader runs.
- LightEdit must offer the `RecentFiles` action only.
- `Ctrl+Tab` on an open full popup must close it and open the quick popup.
- `Ctrl+E` on an open popup must flip the checkbox. It must not open a second popup.

### The layout

- The popup must show the files in the right column.
  [@test] ../../../../tests/remote-driver-tests/test/com/intellij/driver/tests/idea/platform/ui/switcher/SwitcherUiTest.kt
  [@test] ../../../../tests/remote-driver-tests/test/com/intellij/driver/tests/idea/platform/ui/popupsOpening/SwitcherPopupUiTest.kt
  [@test] ../../../../tests/remote-driver-tests/test/com/intellij/driver/tests/idea/platform/ui/popupsOpening/RecentFilesPopupUiTest.kt
  [@test] ../../../../tests/remote-driver-tests/test/com/intellij/driver/tests/idea/platform/ui/RecentFilesUiTest.kt
- The popup must show the available tool windows in the left column.
- The popup must hide the left column when the list of tool windows is empty.
- The full popup must add a `Recent Locations` row to the bottom of the left column.
- The full popup must show the `Show edited only` checkbox in the header.
- The checkbox must switch the file list between the opened kind and the edited kind.
- The checkbox must not rebuild the left column.
- The popup must show the status text of the selected file in a strip at the bottom.
  The strip stays empty when the user selects more than one file.
- The full popup must allow a selection of several files. The quick popup must allow one file.
- The left arrow key and the right arrow key must move the focus between the two columns.
- The empty file list must show `No recent files`. The empty tool window list must show `No tool windows`.

### The keyboard

The quick popup closes when the user releases the modifier key of the launch shortcut. The full popup
stays open. This difference sets the behavior of the other keys:

| Key | The quick popup | The full popup |
| --- | --- | --- |
| Modifier release | Opens the selection and closes | Does nothing |
| Arrow keys | Need the modifier key | Work alone |
| `Enter` | Needs the modifier key | Works alone |
| `Escape` | Needs the modifier key | Works alone |
| Delete | Needs the modifier key | Works alone |

- The popup must keep the modifier keys that the user released before the popup appeared.
  A fast `Ctrl+Tab` must still open the selection and close.
- The selection must move to the other column when it runs off the end of a column.
- `Escape` must close the speed search first, and the popup on the second press.

### Speed search

- The full popup must offer a speed search. The quick popup must not.
  [@test] ../../../../tests/remote-driver-tests/test/com/intellij/driver/tests/idea/platform/ui/recentFiles/SpeedSearchInRecentFilesUiTest.kt
- The speed search must match the main text and the path text of a file.
- The speed search must filter the file list and the tool window list.
- The speed search must mark the matched text in the row.
- The empty file list of a search must show `Press 'Enter' to search in Project`.
- `Enter` with no selection must open the `Go to File` popup with the typed text.

### A row

- The row must show the icon, the main text, and the path text in grey after it.
- The row must drop the path text when the popup is too narrow for it.
- The row must show a red wavy line under the main text of a file with a problem.
- The row must show the foreground colour of the file, except while the user selects the row.
  [@test] ../../../../tests/remote-driver-tests/test/com/intellij/driver/tests/idea/platform/ui/recentFiles/RecentFilesFileStatusUiTest.kt
- The row of a tool window must show its shortcut in the full popup.
- The row of a file must never show a mnemonic.

### Delete a file from the list

- The popup must remove the selected rows when the user presses the delete key.
- The popup must remove the rows from its own list first, before the model answers.
- The popup must close the editors of the file.
- The popup must ask the model to hide the file only when it closed every editor of the file.
- The delete key must close a selected tool window. The row of the tool window must stay.
- The model must also remove the file from the editor history for the `RECENTLY_OPENED` kind.
- The model must keep the file in the editor history for the other two kinds.

### Open a file

- The popup must close first. It must open the selection after that.
- The popup must open the file in the editor window that the row names.
- The popup must ignore that editor window when the user turned the editor tabs off.
  [@test] ../../../../tests/remote-driver-tests/test/com/intellij/driver/tests/idea/platform/ui/switcher/SwitcherUnsplitMultipleFilesTabPlacementNoneTest.kt
- The popup must open every file of a right split selection in one new split.
- The popup must not replace a preview tab with the file that it opens.
- The popup must do nothing for a row whose file is no longer valid.
- The popup must activate a tool window when the user selects a row of the left column.

## Data & Backend

- `FileSwitcherApi` is the single contract between the user interface and the model.
- The contract has one method for a request and one method for the event flow.
- A request must carry the identifier of the project.
- The model must reject a request when it cannot resolve the project.

The contract carries four requests:

| Request | Meaning |
| --- | --- |
| `FetchMetadata` | Send the presentation of the named files again |
| `FetchFiles` | Send the full list of a kind again |
| `HideFiles` | Take the named files out of a kind |
| `ScheduleRehighlighting` | Rehighlight the recent files that no editor shows |

The contract carries four events:

| Event | Meaning |
| --- | --- |
| `ItemsAdded` | Put the files on the top of the list |
| `ItemsUpdated` | Replace the presentation of the files |
| `ItemsRemoved` | Take the files out of the list |
| `AllItemsRemoved` | Clear the list |

- `ItemsUpdated` carries a flag. The flag asks the user interface to move the files to the top.
- `FetchMetadata` carries a flag. The flag asks the model to add an unknown file to the list.
- The user interface must subscribe to the three kinds before it asks for the first list.
- The model must preload the editor history before it answers the first request.

## Error Handling

- The model must answer `false` to a request when it cannot resolve the project.
- A dropped connection must not stop the user interface. It must subscribe again.
- A light session shows no path text, because the session has no file name index.
  Every other field of the row stays correct.
- The user interface must show a file that the model removed as a row with the text `deleted file`.
- The user interface must show a file that is no longer valid as a row with the text `invalidated file`.
- The model must stay inactive in a unit test, unless the test starts the model itself.
  The virtual file system listener and the daemon listener do nothing in a test.

## Testing / Local Run

- The test module is `intellij.platform.recentFiles.tests`. Its production module is
  `intellij.platform.recentFiles`, so a test can read the internal model API.
- The unit test starts both models in one process and connects them by hand. It sets no product
  mode, and it does not use the remote contract.
- A user interface test needs a running IDE. The remote driver runs it, not the unit test runner.
  The directory `tests/remote-driver-tests/test/com/intellij/driver/tests/idea/platform/ui/recentFiles`
  holds more of them.
- A light dev run needs `-Dintellij.platform.product.mode=light` in the virtual machine options.
  The process starts as a monolith without the option.

## Open Questions / Risks

- The startup activity and the virtual file system listener check the host rule one time, when the
  project opens. A later change of the product mode does not restart them. The user interface already
  restarts its own work.
- The listener for a file status change needs the version control platform, so it stays in the
  `backend` module. A light session does not refresh the foreground colour of a row.
- The path text cache keeps the answer on the file for the life of the file. A new file with the same
  name does not make the path text of the older file appear.
- The popup builds the left column one time. The checkbox cannot make the column appear or disappear.
- The texts `deleted file` and `invalidated file` are not in a bundle, so they stay in English.
- No unit test sets a product mode. The light path and the remote contract have no unit test.
  The remote driver suite covers the split pair and the monolith only.
- The old popup reads the process flags, and not the current product mode. A process that changes
  its product mode keeps the choice that it made at the start.
- The events of the model reach a slow consumer as a gap in the list. The user interface cannot learn
  that it lost an event. It must ask for the full list again.

## References

- [Spec format](../../../.ai/spec/SPEC_GUIDE.md)
- [Recent Files Model Hosting](recent-files-model-hosting.spec.md)
