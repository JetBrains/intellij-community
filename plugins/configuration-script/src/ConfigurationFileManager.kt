package com.intellij.configurationScript

import com.intellij.configurationScript.yaml.LightScalarResolver
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.inputStreamIfExists
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.composer.Composer
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.parser.ParserImpl
import org.snakeyaml.engine.v2.scanner.StreamReader
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

// we cannot use the same approach as we generate JSON scheme because we should load option classes only in a lazy manner
// that's why we don't use snakeyaml TypeDescription approach to load
internal class ConfigurationFileManager(project: Project) {
  private val clearableLazyValues = ContainerUtil.createConcurrentList<SynchronizedClearableLazy<*>>()

  private val yamlData = SynchronizedClearableLazy {
    val file = findConfigurationFile(project) ?: return@SynchronizedClearableLazy null
    try {
      val inputStream = file.inputStreamIfExists() ?: return@SynchronizedClearableLazy null
      return@SynchronizedClearableLazy doRead(inputStream.bufferedReader())
    }
    catch (e: Throwable) {
      LOG.error("Cannot parse \"$file\"", e)
    }
    null
  }

  init {
    registerClearableLazyValue(yamlData)
  }

  companion object {
    fun getInstance(project: Project) = project.service<ConfigurationFileManager>()
  }

  fun registerClearableLazyValue(value: SynchronizedClearableLazy<*>) {
    clearableLazyValues.add(value)
  }

  init {
    addFileListener(project)
  }

  private fun addFileListener(project: Project) {
    project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        for (event in events) {
          if (event is VFileCopyEvent) {
            continue
          }

          if (event is VFileCreateEvent) {
            // VFileCreateEvent computes file on request, so, avoid getFile call
            if (event.isDirectory || !isConfigurationFile(event.childName)) {
              continue
            }
          }
          else {
            val file = event.file ?: continue
            if (!isConfigurationFile(file)) {
              continue
            }
          }

          clearableLazyValues.forEach { it.drop() }
        }
      }
    })
  }

  fun getConfigurationNode() = yamlData.value

  // later we can avoid full node graph building, but for now just use simple implementation (problem is that Yaml supports references and merge - proper support of it can be tricky)
  // "load" under the hood uses "compose" - i.e. Yaml itself doesn't use stream API to build object model.
  fun findValueNode(namePath: String): List<NodeTuple>? {
    return findValueNodeByPath(namePath, yamlData.value?.value ?: return null)
  }
}

internal fun doRead(reader: Reader): MappingNode? {
  reader.use {
    val settings = LoadSettings.builder()
      .setUseMarks(false)
      .setScalarResolver(LightScalarResolver())
      .build()
    return Composer(ParserImpl(StreamReader(it, settings), settings), settings.scalarResolver).singleNode.orElse(null) as? MappingNode
  }
}

// todo check parent?
internal fun isConfigurationFile(file: VirtualFile) = isConfigurationFile(file.nameSequence)

private const val filePrefix = "intellij."
private val fileExtensions = listOf("yaml", "yml", "json")

internal fun isConfigurationFile(name: CharSequence): Boolean {
  return name.startsWith(filePrefix) && fileExtensions.any { name.length == (filePrefix.length + it.length) && name.endsWith(it) }
}

/**
 * not-null doesn't mean that you should not expect NoSuchFileException
 */
private fun findConfigurationFile(project: Project): Path? {
  val projectIdeaDir = Paths.get(project.basePath ?: return null)
  var file = projectIdeaDir.resolve("intellij.yaml")
  if (!file.toFile().exists()) {
    // do not check file exists - on read we in any case should check NoSuchFileException
    file = projectIdeaDir.resolve("intellij.yml")
  }
  return file
}

