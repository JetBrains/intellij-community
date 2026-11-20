// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.mcp

import com.intellij.devkit.mcp.DevKitMcpToolset.IntelliJPlatformSourceArchive
import com.intellij.devkit.mcp.DevKitMcpToolset.IntelliJPlatformSourceArchiveKind
import com.intellij.devkit.mcp.DevKitMcpToolset.IntelliJPlatformSources
import com.intellij.java.library.MavenCoordinates
import com.intellij.java.library.getMavenCoordinates
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString

private const val NO_DEPENDENCY_HINT =
  "The project has no IntelliJ Platform dependency. " +
  "The tool supports a project that uses the IntelliJ Platform Gradle Plugin 2.x. " +
  "Sync the Gradle project, then call the tool again."

private const val NO_SOURCES_HINT =
  "No IntelliJ Platform sources are attached. " +
  "The tool does not download sources. " +
  "Enable the download of sources in the Gradle settings of the IDE and sync the Gradle project, " +
  "or open an IntelliJ Platform class in the editor and download its sources. Then call the tool again."

/**
 * The file name of an IntelliJ Platform sources artifact, such as `ideaIC-2024.1-sources.jar`.
 * The artifact IDs are the ones of `IntelliJPlatformSourceCoordinates` in the `intellij.devkit.gradle.tooling` module.
 */
private val PLATFORM_SOURCES_NAME = Regex("""(ideaIC|ideaIU|idea|pycharmPC)-.+-sources\.jar""")

/**
 * Resolves the sources attached to the IntelliJ Platform dependencies of [project].
 *
 * The Gradle sync and `IntelliJPlatformAttachSourcesProvider` of the `intellij.devkit.gradle` module attach the sources to the libraries.
 * This function only reads the sources roots of these libraries. It does not download or attach sources.
 * It reads the root URLs and checks the files on the disk, so the result does not depend on the state of the VFS.
 *
 * The hint tells only that the project has no IntelliJ Platform sources at all.
 * The function does not check that each IntelliJ Platform of the project has its own sources.
 *
 * @return The distinct sources roots that exist, and a hint when none of them is an IntelliJ Platform sources archive.
 */
@ApiStatus.Internal
suspend fun resolveIntelliJPlatformSources(project: Project): IntelliJPlatformSources {
  val sourcesRoots = readAction { collectSourcesRoots(project) }
                     ?: return IntelliJPlatformSources(archives = emptyList(), hint = NO_DEPENDENCY_HINT)

  val archives = withContext(Dispatchers.IO) { sourcesRoots.filter { it.exists() } }
    .map { IntelliJPlatformSourceArchive(it.pathString, archiveKind(it)) }
    .sortedBy { it.kind }
  val hint = if (archives.any { it.kind == IntelliJPlatformSourceArchiveKind.PLATFORM }) null else NO_SOURCES_HINT
  return IntelliJPlatformSources(archives, hint)
}

/**
 * Collects the local paths of the sources roots of the IntelliJ Platform libraries of [project].
 *
 * @return The distinct paths in their natural order, or `null` when the project has no IntelliJ Platform library.
 */
private fun collectSourcesRoots(project: Project): Set<Path>? {
  val libraries = HashSet<Library>()
  val result = sortedSetOf<Path>()
  var hasIntelliJPlatformLibrary = false
  OrderEnumerator.orderEntries(project).librariesOnly().forEachLibrary { library ->
    // the enumerator gives a library one time for each module that uses it
    if (libraries.add(library)) {
      val coordinates = library.getMavenCoordinates()
      if (coordinates != null && isIntelliJPlatformDependency(coordinates)) {
        hasIntelliJPlatformLibrary = true
        library.getUrls(OrderRootType.SOURCES).mapNotNullTo(result, ::toLocalPath)
      }
    }
    true
  }
  return result.takeIf { hasIntelliJPlatformLibrary }
}

/**
 * Checks if [coordinates] belong to an IntelliJ Platform dependency of the IntelliJ Platform Gradle Plugin 2.x.
 * The rules are the ones of `IntelliJPlatformAttachSourcesProvider` in the `intellij.devkit.gradle` module.
 */
private fun isIntelliJPlatformDependency(coordinates: MavenCoordinates): Boolean {
  val groupId = coordinates.groupId
  val artifactId = coordinates.artifactId
  return when {
    // IntelliJ Platform dependency, such as `com.jetbrains.intellij.idea:ideaIC:2023.2.7` or `idea:ideaIC:2024.3`
    IntelliJPlatformProduct.fromMavenCoordinates(groupId, artifactId) != null -> true
    IntelliJPlatformProduct.fromCdnCoordinates(groupId, artifactId) != null -> true
    // IntelliJ Platform local installation, bundled plugin, or bundled module, such as `localIde:IC:2023.2.7+445`
    groupId == "localIde" || groupId == "bundledPlugin" || groupId == "bundledModule" -> true
    // Non-bundled JetBrains plugins with published sources, such as `com.jetbrains.plugins:PythonCore:243.21565.193`
    groupId == "com.jetbrains.plugins" -> artifactId == "PythonCore" || artifactId == "Pythonid"
    else -> false
  }
}

/**
 * Returns the local path of the library root with [url]. For the root of an archive, returns the path of the archive.
 * A root of another file system has no local path.
 */
private fun toLocalPath(url: String): Path? {
  val protocol = VirtualFileManager.extractProtocol(url)
  if (protocol != StandardFileSystems.FILE_PROTOCOL && protocol != StandardFileSystems.JAR_PROTOCOL) {
    return null
  }
  return VfsUtilCore.urlToPath(url).substringBefore(URLUtil.JAR_SEPARATOR).toNioPathOrNull()
}

/**
 * Returns the kind of the sources root in [path].
 * An API archive of a bundled plugin is in the `lib/src` directory of the IntelliJ Platform, such as `lib/src/src_css-api.zip`.
 */
private fun archiveKind(path: Path): IntelliJPlatformSourceArchiveKind {
  val directory = path.parent
  return when {
    PLATFORM_SOURCES_NAME.matches(path.name) -> IntelliJPlatformSourceArchiveKind.PLATFORM
    directory?.name == "src" && directory.parent?.name == "lib" -> IntelliJPlatformSourceArchiveKind.API
    else -> IntelliJPlatformSourceArchiveKind.LIBRARY
  }
}
