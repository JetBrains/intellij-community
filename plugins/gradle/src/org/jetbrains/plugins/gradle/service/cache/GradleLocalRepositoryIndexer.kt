package org.jetbrains.plugins.gradle.service.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.isDirectory
import kotlin.use

@Service(Service.Level.PROJECT)
class GradleLocalRepositoryIndexer(private val project: Project, private val coroutineScope: CoroutineScope) {

  private data class IndexSnapshot(
    val groupIds: Set<String>,
    val group2Artifacts: Map<String, Set<String>>,
    val module2Versions: Map<String, Set<String>>,
    val createdAtMillis: Long,
  ) {
    companion object {
      val EMPTY = IndexSnapshot(emptySet(), emptyMap(), emptyMap(), 0L)
    }
  }

  private suspend fun launchIndex() {
    project.trackActivity(ExternalSystemActivityKey) {
      coroutineScope.launchTracked {
        withContext(Dispatchers.IO) {
          update()
        }
      }
    }
  }

  class Activity : ProjectActivity {
    override suspend fun execute(project: Project) {
      if (GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) return
      project.getService(GradleLocalRepositoryIndexer::class.java).launchIndex()
    }
  }

  private fun update() {
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

      // sort beforehand so that contributors don't have to
      val immutableGroup2Artifacts = group2ArtifactsLocal.mapValues { it.value.toSortedSet() }
      val immutableModule2Versions = module2VersionsLocal.mapValues { it.value.toSortedSet().reversed() } //TODO semantic version sorting?
      val groupIds = immutableGroup2Artifacts.keys.toSortedSet()
      val indexSnapshot = IndexSnapshot(
          groupIds = groupIds,
          group2Artifacts = immutableGroup2Artifacts,
          module2Versions = immutableModule2Versions,
          createdAtMillis = System.currentTimeMillis(),
        )
      val ref = INDICES.computeIfAbsent(eelDescriptor) { AtomicReference(IndexSnapshot.EMPTY) }
      ref.set(indexSnapshot)
    } catch (e: IOException) {
      LOG.error(e)
    } finally {
      LOG.info("Gradle GAV index updated in ${System.currentTimeMillis() - startTime} millis")
      LOG.info("Found $groupNumber groups, $artifactNumber artifacts, $versionNumber versions")
    }
  }

  private fun Path.iterateDirectories(action: (Path) -> Unit) =
    Files.list(this).use { stream -> stream.filter(Files::isDirectory).forEach(action) }

  companion object {
    private val INDICES: ConcurrentHashMap<EelDescriptor, AtomicReference<IndexSnapshot>> = ConcurrentHashMap()

    private fun snapshot(descriptor: EelDescriptor): IndexSnapshot = INDICES[descriptor]?.get() ?: IndexSnapshot.EMPTY

    fun groups(descriptor: EelDescriptor): Collection<String> = snapshot(descriptor).groupIds

    fun artifacts(descriptor: EelDescriptor, groupId: String): Set<String> =
      snapshot(descriptor).group2Artifacts[groupId] ?: emptySet()

    fun versions(descriptor: EelDescriptor, groupId: String, artifactId: String): Set<String> =
      snapshot(descriptor).module2Versions["$groupId:$artifactId"] ?: emptySet()

    private val LOG = logger<GradleLocalRepositoryIndexer>()
  }
}