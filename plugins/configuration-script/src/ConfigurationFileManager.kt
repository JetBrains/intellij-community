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
import org.jetbrains.annotations.ApiStatus
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
  private val clearableLazyValues = ContainerUtil.createConcurrentList<() -> Unit>()

  private val yamlData = SynchronizedClearableLazy {
    val projectIdeaDir = Paths.get(project.basePath ?: return@SynchronizedClearableLazy null)
    readProjectConfigurationFile(projectIdeaDir)
  }

  init {
    registerClearableLazyValue(yamlData)
  }

  companion object {
    fun getInstance(project: Project) = project.service<ConfigurationFileManager>()
  }

  fun registerClearableLazyValue(value: SynchronizedClearableLazy<*>) {
    registerClearableLazyValue { value.drop() }
  }

  fun registerClearableLazyValue(value: () -> Unit) {
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

          clearableLazyValues.forEach { it() }
        }
      }
    })
  }

  fun getConfigurationNode(): MappingNode? = yamlData.value

  fun findValueNode(namePath: String): List<NodeTuple>? {
    val root = getConfigurationNode() ?: return null
    return findValueNode(root, namePath)
  }
}

// later we can avoid full node graph building, but for now just use simple implementation (problem is that Yaml supports references and merge - proper support of it can be tricky)
// "load" under the hood uses "compose" - i.e. Yaml itself doesn't use stream API to build object model.
@ApiStatus.Internal
fun findValueNode(root: MappingNode, namePath: String): List<NodeTuple>? {
  return findValueNodeByPath(namePath, root.value ?: return null)
}

internal fun doRead(reader: Reader): MappingNode? {
  reader.use {
    val settings = LoadSettings.builder()
      .setUseMarks(false)
      .setScalarResolver(LightScalarResolver())
      .build()
    val parser = ParserImpl(StreamReader(it, settings), settings)
    return Composer(parser, settings).singleNode.orElse(null) as? MappingNode
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
private fun findConfigurationFile(projectIdeaDir: Path): Path? {
  var file = projectIdeaDir.resolve("intellij.yaml")
  if (!file.toFile().exists()) {
    // do not check file exists - on read we in any case should check NoSuchFileException
    file = projectIdeaDir.resolve("intellij.yml")
  }
  return file
}

@ApiStatus.Internal
fun readProjectConfigurationFile(projectIdeaDir: Path): MappingNode? {
  val file = findConfigurationFile(projectIdeaDir) ?: return null
  try {
    val inputStream = file.inputStreamIfExists() ?: return null
    return doRead(inputStream.bufferedReader())
  }
  catch (e: Throwable) {
    LOG.error("Cannot parse \"$file\"", e)
  }
  return null
}

