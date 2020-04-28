// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.util.xmlb.annotations.XCollection

private class FileTypeHint: BaseState() {
  @get:XCollection
  val extensions by stringSet()
  @get:XCollection
  val names by stringSet()
  @get:XCollection
  val prefixes by stringSet()
}

private class FileTypeHints : BaseState() {
  var ignored by property(FileTypeHint())
  var text by property(FileTypeHint())
}

private class FileTypeHintPersistentComponent : SimplePersistentStateComponent<FileTypeHints>(FileTypeHints()) {
  private var firstLoading = true

  override fun loadState(state: FileTypeHints) {
    if (state != this.state) {
      super.loadState(state)

      if (firstLoading) {
        firstLoading = false
      } else {
        ApplicationManager.getApplication().invokeLater {
          WriteAction.run<RuntimeException> {
            FileTypeManagerEx.getInstanceEx().fireBeforeFileTypesChanged()
            FileTypeManagerEx.getInstanceEx().fireFileTypesChanged()
          }
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) : FileTypeHintPersistentComponent = project.service()
  }
}

class FileTypeHintService: FileTypeOverrider {
  override fun getOverriddenFileType(file: VirtualFile): FileType? {
    if (file is VirtualFileSystemEntry) {
      val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return null

      val textHint = FileTypeHintPersistentComponent.getInstance(project).state.text
      if (textHint.matches(file)) {
        return PlainTextFileType.INSTANCE
      }
      val ignoredHint = FileTypeHintPersistentComponent.getInstance(project).state.ignored
      if (ignoredHint.matches(file)) {
        return UnknownFileType.INSTANCE
      }

      return null
    }
    return null
  }

  private fun FileTypeHint.matches(file: VirtualFile): Boolean {
    if (extensions.isNotEmpty()) {
      val ext = file.extension
      if (extensions.contains(ext)) {
        return true
      }
    }

    if (names.isNotEmpty() || prefixes.isNotEmpty()) {
      val name = file.nameSequence
      if (names.contains(name)) {
        return true
      }

      for (prefix in prefixes) {
        if (name.startsWith(prefix)) {
          return true
        }
      }
    }

    return false
  }
}