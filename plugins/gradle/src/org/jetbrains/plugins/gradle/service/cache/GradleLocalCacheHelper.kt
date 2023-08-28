// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.cache

import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

@ApiStatus.Experimental
object GradleLocalCacheHelper {

  private const val SOURCE_JAR_SUFFIX = "-sources.jar"
  private const val JAVADOC_JAR_SUFFIX = "-javadoc.jar"
  private const val JAR_SUFFIX = ".jar"
  private const val CACHED_FILES_ROOT_PATH = "caches/modules-2/files-2.1"

  data class ArtifactCoordinates(val groupId: @NlsSafe String,
                                 val artifactId: @NlsSafe String,
                                 val version: @NlsSafe String)

  /**
   * Convert artifact notation to ArtifactCoordinates.
   *
   * @param artifactNotation - artifact notation as a colon-separated String in the format of group:artifact:version.
   * @return ArtifactCoordinates if it was possible to parse the notation, null otherwise.
   */
  @JvmStatic
  fun parseCoordinates(artifactNotation: String): ArtifactCoordinates? = artifactNotation.split(":").let {
    return if (it.size < 3) null else ArtifactCoordinates(it[0], it[1], it[2].removeSuffix("@aar"))
  }

  /**
   * Search for the required artifact components in the Gradle user's home folder.
   * File walker will continue to check files until all requested artifacts are found or until all files have been checked.
   *
   * @param coordinates - the coordinates of the artifact.
   * @param gradleUserHome - the root of the Gradle user home folder to check for an artifact. E.g "/home/user/.gradle/".
   * @param requestedComponents - the set of required components.
   * @return collected artifact components.
   */
  @JvmStatic
  fun findArtifactComponents(coordinates: ArtifactCoordinates,
                             gradleUserHome: Path,
                             requestedComponents: Set<LibraryPathType>): Map<LibraryPathType, List<Path>> {
    if (requestedComponents.isEmpty()) {
      return emptyMap()
    }
    val cachedArtifactRoot = coordinates.toCachedArtifactRoot(gradleUserHome)
    return walkThroughCache(cachedArtifactRoot, requestedComponents)
  }

  /**
   * Search for adjacent components in the Gradle user home folder.
   *
   * @param adjacentComponent - path to the ancestor of the component.
   *                            E.g "%GRADLE_USER_HOME%/caches/modules-2/files-2.1/org.group/artifact/version/"
   * @param requestedComponents - the set of required components.
   * @return collected artifact components.
   */
  @JvmStatic
  fun findAdjacentComponents(adjacentComponent: Path, requestedComponents: Set<LibraryPathType>): Map<LibraryPathType, List<Path>> {
    if (requestedComponents.isEmpty() || !adjacentComponent.toString().contains(CACHED_FILES_ROOT_PATH)) {
      return emptyMap()
    }
    val parent = adjacentComponent.parent
    val cachedArtifactRoot = parent?.parent
    if (cachedArtifactRoot == null) {
      return emptyMap()
    }
    return walkThroughCache(cachedArtifactRoot, requestedComponents)
  }

  private fun walkThroughCache(cachedArtifactRoot: Path,
                               requestedComponents: Set<LibraryPathType>): Map<LibraryPathType, List<Path>> {
    val artifactRoot = cachedArtifactRoot.toFile()
    if (!artifactRoot.exists() || !artifactRoot.isDirectory) {
      return emptyMap()
    }
    return GradleCacheVisitor(cachedArtifactRoot, requestedComponents)
      .let {
        Files.walkFileTree(cachedArtifactRoot, EnumSet.noneOf(FileVisitOption::class.java), 2, it)
        it.target
      }
  }

  private class GradleCacheVisitor(private val cachedArtifactRoot: Path,
                                   private val requestedComponents: Set<LibraryPathType>) : SimpleFileVisitor<Path>() {

    val target: EnumMap<LibraryPathType, MutableList<Path>> = EnumMap(LibraryPathType::class.java)

    @Throws(IOException::class)
    override fun visitFile(sourceCandidate: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (sourceCandidate.parent?.parent != cachedArtifactRoot) {
        return FileVisitResult.SKIP_SIBLINGS
      }
      if (attrs.isRegularFile) {
        val candidateFileName = sourceCandidate.fileName.toString()
        when {
          candidateFileName.endsWith(SOURCE_JAR_SUFFIX) -> target.addPathIfTypeRequired(sourceCandidate, LibraryPathType.SOURCE,
                                                                                        requestedComponents)
          candidateFileName.endsWith(JAVADOC_JAR_SUFFIX) -> target.addPathIfTypeRequired(sourceCandidate, LibraryPathType.DOC,
                                                                                         requestedComponents)
          candidateFileName.endsWith(JAR_SUFFIX) && (candidateFileName.endsWith(JAVADOC_JAR_SUFFIX) || candidateFileName.endsWith(
            SOURCE_JAR_SUFFIX)).not() -> target.addPathIfTypeRequired(sourceCandidate, LibraryPathType.BINARY, requestedComponents)
        }
      }
      return if (target.keys.containsAll(requestedComponents)) {
        return FileVisitResult.TERMINATE
      }
      else {
        super.visitFile(sourceCandidate, attrs)
      }
    }

    private fun EnumMap<LibraryPathType, MutableList<Path>>.addPathIfTypeRequired(path: Path,
                                                                                  type: LibraryPathType,
                                                                                  requiredTypes: Set<LibraryPathType>) {
      if (!requiredTypes.contains(type)) {
        return
      }
      computeIfAbsent(type) { SmartList() }.add(path)
    }
  }

  private fun ArtifactCoordinates.toCachedArtifactRoot(gradleUserHome: Path): Path = gradleUserHome.resolve(CACHED_FILES_ROOT_PATH)
    .resolve(groupId)
    .resolve(artifactId)
    .resolve(version)
}