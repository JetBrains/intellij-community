package com.intellij.configurationScript

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.exists
import com.intellij.util.io.inputStreamIfExists
import org.yaml.snakeyaml.composer.Composer
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.parser.ParserImpl
import org.yaml.snakeyaml.reader.StreamReader
import org.yaml.snakeyaml.resolver.Resolver
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

internal const val IDE_FILE = "intellij.yaml"
internal const val IDE_FILE_VARIANT_2 = "intellij.yml"

internal class ConfigurationFileManager(project: Project) {
  private val clearableLazyValues = ContainerUtil.createConcurrentList<SynchronizedClearableLazy<*>>()
  private val yamlResolver by lazy { Resolver() }

  private val yamlData = SynchronizedClearableLazy {
    if (!Registry.`is`("run.manager.use.intellij.config.file", false)) {
      return@SynchronizedClearableLazy null
    }

    val file = findConfigurationFile(project) ?: return@SynchronizedClearableLazy null
    try {
      val inputStream = file.inputStreamIfExists() ?: return@SynchronizedClearableLazy null
      return@SynchronizedClearableLazy doRead(inputStream.bufferedReader(), yamlResolver)
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
    if (!Registry.`is`("run.manager.use.intellij.config.file", false)) {
      return
    }

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

internal fun doRead(reader: Reader, resolver: Resolver = Resolver()): MappingNode? {
  reader.use {
    val composer = Composer(ParserImpl(StreamReader(it)), resolver)
    return composer.singleNode as? MappingNode
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
  val projectIdeaDir = Paths.get(project.basePath)
  var file = projectIdeaDir.resolve("intellij.yaml")
  if (!file.exists()) {
    // do not check file exists - on read we in any case should check NoSuchFileException
    file = projectIdeaDir.resolve("intellij.yml")
  }
  return file
}

