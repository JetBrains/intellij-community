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
 * Returns `true` until the process advances from light to frontend mode. Unlike `Project.isRdLightFrontend`, this value
 * becomes `false` after the upgrade.
 */
fun Driver.isLightSession(): Boolean = utility<IdeProductMode>().isLight()

/**
 * Opens [relativePath] directly in a light frontend and waits until it is selected. The regular remote-dev navigation
 * service is unavailable before a backend connects, and [Project.getBasePath] points to a synthetic directory.
 */
internal fun Driver.openFileInLightSession(relativePath: String, project: Project, isTextEditor: Boolean): VirtualFile {
  val fileToOpen = waitFor(
    message = "File is in the frontend VFS: $relativePath",
    errorMessage = { "Fail to find file $relativePath" },
    timeout = 10.seconds,
    getter = { lightProjectFile(relativePath, project) },
    checker = { it != null }
  ) ?: error("File $relativePath not found")

  // Startup may select the README after this file opens, so retry the open until our file remains selected.
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
 * Refreshes and finds [relativePath] under the light project directory. Do not call it from an off-EDT read action: the
 * synchronous refresh waits for VFS events applied under an EDT write action.
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

@Remote("com.intellij.openapi.vfs.VirtualFileManager")
private interface VirtualFileManagerRef {
  fun refreshAndFindFileByUrl(url: String): VirtualFile?
}

private const val FILE_PROTOCOL_PREFIX = "file://"

@Remote("com.intellij.platform.frontend.split.base.light.IjLightUpgradeService",
        plugin = "com.jetbrains.remoteDevelopment/intellij.platform.frontend.split.base")
private interface IjLightUpgradeService {
  fun lightProjectPath(project: Project): String?
}

/** Returns the selected text editor, or `null` when another editor type is selected. */
fun Driver.selectedTextEditor(project: Project = singleProject()): Editor? =
  service<FileEditorManager>(project).getSelectedTextEditor()

/** The files of all currently open editors, in no particular order. */
fun Driver.openEditorFiles(project: Project = singleProject()): List<VirtualFile> =
  service<FileEditorManager>(project).getAllEditors().map { it.getFile() }
