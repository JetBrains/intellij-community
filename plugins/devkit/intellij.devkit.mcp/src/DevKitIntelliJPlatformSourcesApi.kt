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
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct
import org.jetbrains.idea.devkit.run.loadProductInfo
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString

private const val NO_DEPENDENCY_HINT =
  "The project has no IntelliJ Platform dependency. " +
  "The tool supports a project that uses the IntelliJ Platform Gradle Plugin 2.x. " +
  "Sync the Gradle project, then call the tool again."

private const val NO_SOURCES_HINT =
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
 * @return The distinct sources roots that exist, and a hint when an IntelliJ Platform has no IntelliJ Platform sources.
 */
@ApiStatus.Internal
suspend fun resolveIntelliJPlatformSources(project: Project): IntelliJPlatformSources {
  val libraries = readAction { collectIntelliJPlatformLibraries(project) }
  if (libraries.isEmpty()) {
    return IntelliJPlatformSources(archives = emptyList(), hint = NO_DEPENDENCY_HINT)
  }

  val (platformVersions, existingRoots) = withContext(Dispatchers.IO) {
    resolvePlatformVersions(libraries) to libraries.flatMap { it.sourcesRoots }.filterTo(HashSet()) { it.exists() }
  }
  val versionsPerArchive = sortedMapOf<Path, MutableSet<String>>()
  for (library in libraries) {
    for (sourcesRoot in library.sourcesRoots) {
      if (sourcesRoot in existingRoots) {
        versionsPerArchive.getOrPut(sourcesRoot) { sortedSetOf() }.add(platformVersions.getValue(library))
      }
    }
  }

  val archives = versionsPerArchive
    .map { (path, versions) -> IntelliJPlatformSourceArchive(path.pathString, archiveKind(path), versions.toList()) }
    .sortedBy { it.kind }
  val versionsWithSources = archives
    .filter { it.kind == IntelliJPlatformSourceArchiveKind.PLATFORM }
    .flatMapTo(HashSet()) { it.platformVersions }
  val versionsWithoutSources = platformVersions.values.toSortedSet() - versionsWithSources
  val hint = if (versionsWithoutSources.isEmpty()) {
    null
  }
  else {
    "No IntelliJ Platform sources are attached for: ${versionsWithoutSources.joinToString()}. $NO_SOURCES_HINT"
  }
  return IntelliJPlatformSources(archives, hint)
}

/**
 * A library of an IntelliJ Platform dependency.
 *
 * @param version The version from the Maven coordinates of the library.
 * @param classesRoot The local path of the first classes root.
 * @param sourcesRoots The local paths of the sources roots.
 */
private class IntelliJPlatformLibrary(val version: String, val classesRoot: Path?, val sourcesRoots: List<Path>)

private fun collectIntelliJPlatformLibraries(project: Project): List<IntelliJPlatformLibrary> {
  val result = ArrayList<IntelliJPlatformLibrary>()
  OrderEnumerator.orderEntries(project).librariesOnly().forEachLibrary { library ->
    val coordinates = library.getMavenCoordinates()
    if (coordinates != null && isIntelliJPlatformDependency(coordinates)) {
      result += IntelliJPlatformLibrary(
        version = coordinates.version,
        classesRoot = library.getUrls(OrderRootType.CLASSES).firstNotNullOfOrNull(::toLocalPath),
        sourcesRoots = library.getUrls(OrderRootType.SOURCES).mapNotNull(::toLocalPath),
      )
    }
    true
  }
  return result
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
 * Resolves the IntelliJ Platform version of every library in [libraries].
 *
 * The version is `<product code>-<build number>` from the `product-info.json` file of the IntelliJ Platform that holds the library.
 * The libraries of one IntelliJ Platform have coordinates with different version formats, and this version is the same for all of them.
 * When no directory above the library holds the file, the version is the one of the library coordinates.
 */
private fun resolvePlatformVersions(libraries: List<IntelliJPlatformLibrary>): Map<IntelliJPlatformLibrary, String> {
  val versionsPerDirectory = HashMap<Path, String>()
  return libraries.associateWith { library ->
    val version = library.classesRoot?.parent?.let { directory ->
      versionsPerDirectory.getOrPut(directory) { readPlatformVersion(directory).orEmpty() }
    }
    if (version.isNullOrEmpty()) library.version else version
  }
}

private fun readPlatformVersion(directory: Path): String? =
  generateSequence(directory) { it.parent }
    .takeWhile { it != it.root }
    .firstNotNullOfOrNull { loadProductInfo(it.pathString) }
    ?.let { "${it.productCode}-${it.buildNumber}" }

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
