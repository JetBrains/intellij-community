package com.intellij.configurationScript

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
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
import org.yaml.snakeyaml.parser.ParserImpl
import org.yaml.snakeyaml.reader.StreamReader
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

internal const val IDE_FILE = "intellij.yaml"
internal const val IDE_FILE_VARIANT_2 = "intellij.yml"

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
            if (event.isDirectory || !(event.childName == IDE_FILE || event.childName == IDE_FILE_VARIANT_2)) {
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
}

internal fun doRead(reader: Reader): MappingNode? {
  reader.use {
    return LightweightComposer(ParserImpl(StreamReader(it))).getSingleNode() as? MappingNode
  }
}

// todo check parent?
internal fun isConfigurationFile(file: VirtualFile): Boolean {
  val nameSequence = file.nameSequence
  return StringUtil.equals(nameSequence, IDE_FILE) || StringUtil.equals(nameSequence, IDE_FILE_VARIANT_2)
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

