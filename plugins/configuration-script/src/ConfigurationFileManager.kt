package com.intellij.configurationScript

import com.intellij.ide.impl.isTrusted
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
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
import org.snakeyaml.engine.v2.schema.FailsafeSchema
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

// we cannot use the same approach as we generate JSON scheme because we should load option classes only in a lazy manner
// that's why we don't use snakeyaml TypeDescription approach to load
@Service(Service.Level.PROJECT)
internal class ConfigurationFileManager(project: Project): Disposable {
  private val clearableLazyValues = ContainerUtil.createConcurrentList<() -> Unit>()

  private val yamlData = SynchronizedClearableLazy {
    if (!project.isTrusted()) {
      return@SynchronizedClearableLazy null
    }
    val projectIdeaDir = Paths.get(project.basePath ?: return@SynchronizedClearableLazy null)
    readProjectConfigurationFile(projectIdeaDir)
  }

  init {
    if (!project.isTrusted()) {
      TrustedProjectsListener.onceWhenProjectTrusted(this) {
        clearClearableValues()
      }
    }
    registerClearableLazyValue(yamlData)
    addFileListener(project)
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

  private fun clearClearableValues() {
    clearableLazyValues.forEach { it() }
  }

  private fun addFileListener(project: Project) {
    project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        for (event in events) {
          if (event is VFileCopyEvent) {
            continue
          }

          if (event is VFileCreateEvent) {
            // VFileCreateEvent computes a file on request, so, avoid getFile call
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

          clearClearableValues()
        }
      }
    })
  }

  fun getConfigurationNode(): MappingNode? = yamlData.value

  fun findValueNode(namePath: String): List<NodeTuple>? {
    val root = getConfigurationNode() ?: return null
    return findValueNode(root, namePath)
  }

  override fun dispose() = Unit
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
      .setSchema(FailsafeSchema())
      .build()
    val parser = ParserImpl(settings, StreamReader(settings, it))
    return Composer(settings, parser).singleNode.orElse(null) as? MappingNode
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

