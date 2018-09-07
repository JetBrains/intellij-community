package com.intellij.configurationScript

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.containers.ContainerUtil

internal const val IDE_FILE = "intellij.yaml"
internal const val IDE_FILE_VARIANT_2 = "intellij.yml"

internal class ConfigurationFileManager(project: Project) {
  private val clearableLazyValues = ContainerUtil.createConcurrentList<ClearableLazyValue<*>>()

  fun registerClearableLazyValue(value: ClearableLazyValue<*>) {
    clearableLazyValues.add(value)
  }

  init {
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
}

// todo check parent?
internal fun isConfigurationFile(file: VirtualFile): Boolean {
  val nameSequence = file.nameSequence
  return StringUtil.equals(nameSequence, IDE_FILE) || StringUtil.equals(nameSequence, IDE_FILE_VARIANT_2)
}