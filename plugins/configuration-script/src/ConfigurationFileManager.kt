package com.intellij.configurationScript

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
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.parser.ParserImpl
import org.yaml.snakeyaml.reader.StreamReader
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

  fun findValueNode(namePath: String): MappingNode? {
    return findValueNodeByPath(namePath, yamlData.value ?: return null)
  }
}

internal fun findValueNodeByPath(namePath: String, rootNode: MappingNode): MappingNode? {
  var node = rootNode
  loop@
  for (name in namePath.splitToSequence('.')) {
    for (tuple in node.value) {
      val keyNode = tuple.keyNode
      if (keyNode is ScalarNode && keyNode.value == name) {
        node = tuple.valueNode as? MappingNode ?: continue
        continue@loop
      }
    }
    return null
  }

  return if (node === rootNode) null else node
}

internal fun doRead(reader: Reader): MappingNode? {
  reader.use {
    return LightweightComposer(ParserImpl(StreamReader(it))).getSingleNode() as? MappingNode
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

