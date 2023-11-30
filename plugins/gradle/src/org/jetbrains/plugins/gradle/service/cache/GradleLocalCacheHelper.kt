// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.cache

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

@ApiStatus.Experimental
object GradleLocalCacheHelper {

  private const val SOURCE_JAR_SUFFIX = "-sources.jar"
  private const val JAVADOC_JAR_SUFFIX = "-javadoc.jar"
  private const val JAR_SUFFIX = ".jar"
  private const val CACHED_FILES_ROOT_PATH = "caches/modules-2/files-2.1"

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
  fun findArtifactComponents(coordinates: UnifiedCoordinates,
                             gradleUserHome: Path,
                             requestedComponents: Set<LibraryPathType>): Map<LibraryPathType, List<Path>> = coordinates.toCachedArtifactRoot(
    gradleUserHome)?.let { findAdjacentComponents(it, requestedComponents) } ?: emptyMap()

  /**
   * Search for adjacent components in the Gradle artifact cache folder.
   *
   * @param cachedArtifactRoot - path to the ancestor of the component.
   *                            E.g "%GRADLE_USER_HOME%/caches/modules-2/files-2.1/org.group/artifact/version/"
   * @param requestedComponents - the set of required components.
   * @return collected artifact components.
   */
  @JvmStatic
  fun findAdjacentComponents(cachedArtifactRoot: Path, requestedComponents: Set<LibraryPathType>): Map<LibraryPathType, List<Path>> {
    if (!cachedArtifactRoot.exists() || !cachedArtifactRoot.isDirectory()) {
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
          candidateFileName.endsWith(JAR_SUFFIX) -> target.addPathIfTypeRequired(sourceCandidate, LibraryPathType.BINARY,
                                                                                 requestedComponents)
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

  private fun UnifiedCoordinates.toCachedArtifactRoot(gradleUserHome: Path): Path? {
    if (groupId == null || artifactId == null || version == null) {
      return null
    }
    return gradleUserHome.resolve(CACHED_FILES_ROOT_PATH)
      .resolve(groupId)
      .resolve(artifactId)
      .resolve(version)
  }
}