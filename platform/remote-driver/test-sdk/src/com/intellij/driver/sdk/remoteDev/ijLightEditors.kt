package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.openEditor
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitFor
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Driver support for frontends started in IJ Light mode, i.e. launched with the `ijLight <projectPath>` command and not
 * connected to a backend yet.
 *
 * Kept apart from `editors.kt` on purpose: everything here is specific to that one mode, while `editors.kt` is shared by
 * every driver test in the monorepo. The only thing this file cannot own is the branch that routes `openFile` here.
 */

/**
 * `true` while this process is still an IJ Light session, i.e. before it has fully advanced to a connected frontend.
 *
 * Backed by the platform's `LIGHT -> LIGHT_WITH_RD_CONNECTION -> FRONTEND` transition, so it flips on its own once the
 * upgrade completes. Do not substitute `Project.isRdLightFrontend` for it - that one is set at project open and never
 * reset, so it stays `true` on an upgraded session.
 */
fun Driver.isLightSession(): Boolean = utility<IdeProductMode>().isLight()

/**
 * Opens [relativePath] on a light frontend.
 *
 * Neither half of `openFile`'s usual remote-dev route exists in that state: `FrontendGuestNavigationService` and
 * `FrontendProjectRootManager` both live in `com.jetbrains.performancePlugin/intellij.performanceTesting.frontend.split`,
 * a module the light frontend does not load, so calling them fails inside the Driver with
 * `NullPointerException at Invoker.getClassLoader`. And the light project carries no modules
 * (`IjLightStarter` opens it with `createModule = false`), so there are no content roots to resolve against either.
 *
 * The path is therefore resolved against the directory the light session was started with - [Project.getBasePath] is the
 * synthetic `ThinClientProjectUtil` directory, not the project - and opened straight through `FileEditorManager`.
 *
 * Returns once the file is the current editor, so that `openFile` means the same thing here as it does on a connected
 * frontend, where the remote-dev branch ends with `findCurrentEditorFile`.
 */
internal fun Driver.openFileInLightSession(relativePath: String, project: Project, isTextEditor: Boolean): VirtualFile {
  val fileToOpen = waitFor(
    message = "File is in the frontend VFS: $relativePath",
    errorMessage = { "Fail to find file $relativePath" },
    timeout = 10.seconds,
    getter = { lightProjectFile(relativePath, project) },
    checker = { it != null }
  ) ?: error("File $relativePath not found")

  // The open is retried rather than done once and waited on. A light frontend opens the project's own README while it
  // is still starting up, and an open that lands in that window cannot take the selection - the IDE logs
  // `Cannot focus editor ... reason=selection changed` and keeps the README composite current. The file is then open
  // but not current, and no amount of waiting alone makes it current; re-opening it does.
  return waitFor(message = "File is the current editor: $relativePath",
                 errorMessage = { "Current editor is ${it?.getPath()}" },
                 timeout = 30.seconds,
                 getter = {
                   openEditor(fileToOpen, project)
                   if (isTextEditor) service<FileEditorManager>(project).getSelectedTextEditor()?.getVirtualFile()
                   else service<FileEditorManager>(project).getCurrentFile()
                 },
                 checker = { it != null && Path.of(it.getPath()).endsWith(Path.of(relativePath)) })!!
}

/**
 * Resolves [relativePath] inside the directory the light session was started with, or `null` when there is no such file.
 * Pass an empty path to get the project directory itself.
 *
 * The lookup refreshes the VFS on the way (`refreshAndFind` rather than plain `find`), because a light session opens the
 * project without indexing it, so nothing has necessarily pulled the file in yet - and a file the test has just created
 * would not be there at all.
 *
 * Must not be called inside a read action off the EDT: the refresh waits for VFS events, which are fired on the EDT under
 * a write action, so a read action around it deadlocks (see `VirtualFileManager.refreshAndFindFileByUrl`). Plain Driver
 * calls are off-EDT and lock-free, so this is fine unless a caller wraps it in `withReadAction`.
 */
fun Driver.lightProjectFile(relativePath: String = "", project: Project = singleProject()): VirtualFile? {
  val projectPath = service<IjLightUpgradeService>().lightProjectPath(project)
                    ?: error("Not an IJ Light project: no light project path on $project")
  val path = if (relativePath.isEmpty()) Path.of(projectPath) else Path.of(projectPath, relativePath)
  return service<VirtualFileManagerRef>().refreshAndFindFileByUrl("$FILE_PROTOCOL_PREFIX$path")
}

@Remote("com.intellij.platform.ide.productMode.IdeProductMode")
private interface IdeProductMode {
  fun isLight(): Boolean
}

/**
 * `VirtualFileManager` is an application service (`PlatformExtensions.xml`), so unlike `LocalFileSystem` - which is only
 * reachable through its static `getInstance()` - it needs no factory method here.
 */
@Remote("com.intellij.openapi.vfs.VirtualFileManager")
private interface VirtualFileManagerRef {
  fun refreshAndFindFileByUrl(url: String): VirtualFile?
}

/** `StandardFileSystems.FILE_PROTOCOL_PREFIX`, inlined to keep this file free of platform imports. */
private const val FILE_PROTOCOL_PREFIX = "file://"

@Remote("com.intellij.platform.frontend.split.base.light.IjLightUpgradeService",
        plugin = "com.jetbrains.remoteDevelopment/intellij.platform.frontend.split.base")
private interface IjLightUpgradeService {
  fun lightProjectPath(project: Project): String?
}

/**
 * The [Editor] of the currently selected text editor tab, or `null` when the selected tab is not a text editor (or there
 * is none) - nullable like the finders in `editors.kt`, so that callers keep their own wording for the failure.
 *
 * Named after `FileEditorManager.getSelectedTextEditor` rather than "selected editor" on purpose: the platform's
 * `getSelectedEditor` is a different thing, a `FileEditor` rather than an [Editor].
 *
 * Not actually specific to light sessions - it lives here only to keep the shared `editors.kt` free of changes, and could
 * be promoted there whenever that file is being touched anyway. Note the three places that still spell this call out by
 * hand: `Driver.selectedEditorFileName` in `kotlin.uiPluginTests` and two of them in `ProblemsViewHighlightingUiTest`.
 */
fun Driver.selectedTextEditor(project: Project = singleProject()): Editor? =
  service<FileEditorManager>(project).getSelectedTextEditor()

/** The files of all currently open editors, in no particular order. */
fun Driver.openEditorFiles(project: Project = singleProject()): List<VirtualFile> =
  service<FileEditorManager>(project).getAllEditors().map { it.getFile() }

/**
 * There is deliberately no "select this file's tab" helper here.
 *
 * `FileEditorManager` has no such API: `setSelectedEditor(file, providerId)` picks the *provider* inside one file's editor
 * (Text vs Preview) - `getComposite(file)` then `composite.setSelectedEditor(providerId)` - and never changes which file is
 * current, while the `setSelectedEditor(FileEditor)` the shared `editors.kt` proxy declares exists on neither
 * `FileEditorManager` nor `FileEditorManagerEx`. Bringing an already open file to front *is* `openFile`, which selects the
 * existing composite instead of re-reading the file, so tests switch with `openFile` and assert that unsaved state stays.
 */
