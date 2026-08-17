package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.findCurrentEditorFile
import com.intellij.driver.sdk.openEditor
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitNotNull
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
  val fileToOpen = waitNotNull(
    message = "File is in the frontend VFS: $relativePath",
    errorMessage = { "Fail to find file $relativePath" },
    timeout = 10.seconds,
    getter = { lightProjectFile(relativePath, project) }
  )

  openEditor(fileToOpen, project)
  return findCurrentEditorFile(relativePath = relativePath, project = project, isTextEditor = isTextEditor)!!
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

/** The files of all currently open editors, in no particular order. */
fun Driver.openEditorFiles(project: Project = singleProject()): List<VirtualFile> =
  service<FileEditorManager>(project).getAllEditors().map { it.getFile() }

/**
 * Normalizes Project View paths whose presentation changes when IJ Light upgrades to Smart Mode.
 * Compacted directory names are split into separate path segments, and leaf names are compared without their final extension.
 *
 * For example:
 * ```
 * listOf(
 *   "MyProject",
 *   "MyProject/src",
 *   "MyProject/src/com.example",
 *   "MyProject/src/com.example/App.java",
 * ).toCanonicalProjectPaths("MyProject")
 *
 * // Result:
 * setOf(
 *   listOf("src"),
 *   listOf("src", "com"),
 *   listOf("src", "com", "example"),
 *   listOf("src", "com", "example", "App"),
 * )
 * ```
 */
fun List<String>.toCanonicalProjectPaths(projectName: String): Set<List<String>> {
  fun List<String>.prefixes(): List<List<String>> =
    (1 .. size).map { length -> take(length) }

  val projectRoot = asSequence()
    .filter { it.startsWith(projectName) }
    .minByOrNull { it.length }
    ?: error("Project root not found: $projectName")
  val projectRootPrefix = "$projectRoot/"

  val visiblePaths = asSequence()
    .filter { it.startsWith(projectRootPrefix) }
    .map { it.removePrefix(projectRootPrefix).split('/') }
    .filterNot { path -> path.any { node -> node.startsWith('.') } }
    .toSet()

  val directoryPaths = visiblePaths
    .flatMap { path -> path.prefixes().dropLast(1) }
    .toSet()

  val canonicalPaths = visiblePaths.map { path ->
    path.flatMapIndexed { index, node ->
      if (path.take(index + 1) in directoryPaths) {
        node.split('.')
      }
      else {
        listOf(node.substringBeforeLast('.', missingDelimiterValue = node))
      }
    }
  }

  return canonicalPaths.flatMap { path -> path.prefixes() }.toSet()
}
