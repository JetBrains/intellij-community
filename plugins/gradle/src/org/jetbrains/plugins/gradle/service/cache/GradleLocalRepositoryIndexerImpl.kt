// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.application
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.SortedSet
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.isDirectory

@ApiStatus.Internal
open class GradleLocalRepositoryIndexerImpl : GradleLocalRepositoryIndexer {
  @Service(Service.Level.APP)
  private class CoroutineScopeProvider(val coroutineScope: CoroutineScope)

  protected data class IndexSnapshot(
    private val group2ArtifactsLocal: Map<String, Collection<String>>,
    private val module2VersionsLocal: Map<String, Collection<String>>,
  ) {
    // sort beforehand so that contributors don't have to
    val group2Artifacts: Map<String, SortedSet<String>> = group2ArtifactsLocal.mapValues { it.value.toSortedSet() }
    val artifact2Groups: Map<String, Set<String>> = group2Artifacts
      .flatMap { (group, artifacts) -> artifacts.map { artifact -> artifact to group } }
      .groupBy({ it.first }, { it.second })
      .mapValues { (_, groups) -> groups.toSortedSet() }
    val module2Versions: Map<String, SortedSet<String>> = module2VersionsLocal.mapValues {
      TreeSet(VersionComparatorUtil.COMPARATOR).apply { addAll(it.value) }.descendingSet()
    }
    val groupIds: SortedSet<String> = group2Artifacts.keys.toSortedSet()
    val artifactIds: SortedSet<String> = artifact2Groups.keys.toSortedSet()
    val createdAtMillis: Long = System.currentTimeMillis()
    companion object {
      val EMPTY: IndexSnapshot = IndexSnapshot(emptyMap(), emptyMap())
    }
  }

  protected val indices: ConcurrentHashMap<EelDescriptor, AtomicReference<IndexSnapshot>> = ConcurrentHashMap()

  private fun snapshot(descriptor: EelDescriptor): IndexSnapshot = indices[descriptor]?.get() ?: IndexSnapshot.EMPTY

  override fun groups(descriptor: EelDescriptor): Collection<String> = snapshot(descriptor).groupIds

  override fun groups(descriptor: EelDescriptor, artifactId: String): Set<String> =
    snapshot(descriptor).artifact2Groups[artifactId] ?: emptySet()

  override fun artifacts(descriptor: EelDescriptor): Set<String> = snapshot(descriptor).artifactIds

  override fun artifacts(descriptor: EelDescriptor, groupId: String): Set<String> =
    snapshot(descriptor).group2Artifacts[groupId] ?: emptySet()

  override fun versions(descriptor: EelDescriptor, groupId: String, artifactId: String): Set<String> =
    snapshot(descriptor).module2Versions["$groupId:$artifactId"] ?: emptySet()

  override fun launchIndexUpdate(project: Project) {
    service<CoroutineScopeProvider>().coroutineScope.launchTracked {
      withContext(Dispatchers.IO) {
        update(project)
      }
    }
  }

  class GradleLocalRepositoryIndexInitializer : ProjectActivity {
    override suspend fun execute(project: Project) {
      if (GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) return
      service<GradleLocalRepositoryIndexer>().let { indexer ->
        project.trackActivity(ExternalSystemActivityKey) {
          indexer.launchIndexUpdate(project)
        }
      }
    }
  }

  class GradleLocalRepositoryIndexUpdater : ExternalSystemTaskNotificationListener {
    override fun onEnd(proojecPath: String, id: ExternalSystemTaskId) {
      if (id.projectSystemId == GradleConstants.SYSTEM_ID && id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
        val project = id.findProject() ?: return
        service<GradleLocalRepositoryIndexer>().let { indexer ->
          project.trackActivityBlocking(ExternalSystemActivityKey) {
            indexer.launchIndexUpdate(project)
          }
        }
      }
    }
  }

  private fun update(project: Project) {
    if (!useDependencyCompletionService()) {
      return
    }

    var groupNumber = 0
    var artifactNumber = 0
    var versionNumber = 0
    val startTime = System.currentTimeMillis()
    try {
      val eelDescriptor = project.getEelDescriptor()
      // expected structure: <GRADLE_USER_HOME>/caches/modules-2/files-2.1/<group>/<artifact>/<version>/...
      val files21 = gradleUserHomeDir(eelDescriptor)
        .resolve("caches")
        .resolve("modules-2")
        .resolve("files-2.1")

      if (!files21.isDirectory()) throw IOException("Cannot find files-2.1 directory at $files21")

      val group2ArtifactsLocal = HashMap<String, MutableSet<String>>()
      val module2VersionsLocal = HashMap<String, MutableSet<String>>()

      files21.iterateDirectories { groupDir ->
        val group = groupDir.fileName.toString()
        groupDir.iterateDirectories { artifactDir ->
          val artifact = artifactDir.fileName.toString()
          group2ArtifactsLocal.computeIfAbsent(group) { LinkedHashSet() }.add(artifact)
          val ga = "$group:$artifact"
          artifactDir.iterateDirectories { versionDir ->
            val version = versionDir.fileName.toString()
            module2VersionsLocal.computeIfAbsent(ga) { LinkedHashSet() }.add(version)
            versionNumber++
          }
          artifactNumber++
        }
        groupNumber++
      }

      val indexSnapshot = IndexSnapshot(group2ArtifactsLocal, module2VersionsLocal)
      val ref = indices.computeIfAbsent(eelDescriptor) { AtomicReference(IndexSnapshot.EMPTY) }
      ref.set(indexSnapshot)
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    finally {
      LOG.info("Gradle GAV index updated in ${System.currentTimeMillis() - startTime} millis")
      LOG.info("Found $groupNumber groups, $artifactNumber artifacts, $versionNumber versions")
    }
  }

  private fun Path.iterateDirectories(action: (Path) -> Unit) =
    Files.list(this).use { stream -> stream.filter(Files::isDirectory).forEach(action) }

  companion object {
    private val LOG = logger<GradleLocalRepositoryIndexerImpl>()
  }
}

@ApiStatus.Internal
@TestOnly
class GradleLocalRepositoryIndexerTestImpl(
  eelDescriptor: EelDescriptor? = null,
  gavList: List<String> = emptyList(),
) : GradleLocalRepositoryIndexerImpl() {

  constructor(eelDescriptor: EelDescriptor, vararg gavArgs: String) : this(eelDescriptor, gavArgs.toList())

  /**
   * Constructor expects arguments in gav format with colons: `<group>:<artifact>:<version>`
   */
  init {
    gavList.forEach { gav ->
      val parts = gav.split(":")
      require(parts.size == 3 && parts.all(String::isNotBlank)) {
        "Invalid GAV format: $gav, GradleLocalRepositoryIndexerTestImpl requires arguments in gav format: `<group>:<artifact>:<version>`"
      }
    }

    val entries = gavList.map { it.split(":") }.map { Triple(it[0], it[1], it[2]) }

    val group2Artifacts = entries
      .groupBy { it.first }
      .mapValues { (_, triples) ->
        triples.map { it.second }
      }

    val module2Versions = entries
      .groupBy { "${it.first}:${it.second}" }
      .mapValues { (_, triples) ->
        triples.map { it.third }
      }

    val indexSnapshot = IndexSnapshot(group2Artifacts, module2Versions)
    eelDescriptor?.let {
      val ref = indices.computeIfAbsent(eelDescriptor) { AtomicReference(IndexSnapshot.EMPTY) }
      ref.set(indexSnapshot)
    }
  }

  override fun launchIndexUpdate(project: Project) {} // do nothing
}