package org.jetbrains.plugins.gradle.service.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.util.containers.prefixTree.set.toPrefixTreeSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.GradleCoroutineScope
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Files.isDirectory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isDirectory
import kotlin.use

@Service(Service.Level.PROJECT)
class GradleLocalRepositoryIndex(private val project: Project, private val coroutineScope: CoroutineScope) {

  private data class Snapshot(
    val groupIds: Set<String>,
    val group2Artifacts: Map<String, Set<String>>,
    val module2Versions: Map<String, Set<String>>,
    val createdAtMillis: Long,
  ) {
    companion object {
      val EMPTY = Snapshot(emptySet(), emptyMap(), emptyMap(), 0L)
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
      project.getService(GradleLocalRepositoryIndex::class.java).launchIndex()
    }
  }

  private fun update() {
    var groupNumber = 0
    var artifactNumber = 0
    var versionNumber = 0
    val startTime = System.currentTimeMillis()
    try {
      // expected structure: <GRADLE_USER_HOME>/caches/modules-2/files-2.1/<group>/<artifact>/<version>/...
      val files21 = gradleUserHomeDir(project.getEelDescriptor())
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
      val snapshot = Snapshot(
          groupIds = groupIds,
          group2Artifacts = immutableGroup2Artifacts,
          module2Versions = immutableModule2Versions,
          createdAtMillis = System.currentTimeMillis(),
        )
      GLOBAL.set(snapshot)
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
    private val GLOBAL = java.util.concurrent.atomic.AtomicReference(Snapshot.EMPTY)
    @JvmStatic fun groups(): Collection<String> = GLOBAL.get().groupIds
    @JvmStatic fun artifacts(groupId: String): Set<String> = GLOBAL.get().group2Artifacts[groupId] ?: emptySet()
    @JvmStatic fun versions(groupId: String, artifactId: String): Set<String> = GLOBAL.get().module2Versions["$groupId:$artifactId"] ?: emptySet()
    private val LOG = logger<GradleLocalRepositoryIndex>()
  }
}