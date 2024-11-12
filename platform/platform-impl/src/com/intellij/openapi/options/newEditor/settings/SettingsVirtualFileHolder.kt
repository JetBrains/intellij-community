// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.options.newEditor.AbstractEditor
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.resettableLazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.JComponent

@Service(Level.PROJECT)
class SettingsVirtualFileHolder private constructor (private val project: Project){
  companion object {
    @JvmStatic
    fun getInstance(project: Project) : SettingsVirtualFileHolder {
      return project.getService(SettingsVirtualFileHolder::class.java)
    }
  }

  private val settingsFileRef = AtomicReference<SettingsVirtualFile?>(null)

  suspend fun getOrCreate(initializer: () -> SettingsDialog) : SettingsVirtualFile {
    return withContext(Dispatchers.EDT) {
      val settingsVirtualFile = settingsFileRef.get()

      if (settingsVirtualFile != null) {
        return@withContext settingsVirtualFile
      }
      val settingsDialog = initializer.invoke()
      val newVirtualFile = SettingsVirtualFile(settingsDialog.editor, project)
      settingsDialog.addChildDisposable {
        val fileEditorManager = FileEditorManager.getInstance(newVirtualFile.project) as FileEditorManagerEx;
        fileEditorManager.closeFile(newVirtualFile)
        settingsFileRef.set(null)
      }
      settingsFileRef.compareAndSet(null, newVirtualFile)
      return@withContext newVirtualFile
    }
  }

  class SettingsVirtualFile(val editor: AbstractEditor, val project: Project) :
    LightVirtualFile(CommonBundle.settingsTitle(), SettingFileType(), ""), OptionallyIncluded {

    init {
      putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    }

    override fun isIncludedInEditorHistory(project: Project): Boolean  = false
  }

  private class SettingFileType: FakeFileType() {
    override fun getName(): @NonNls String  = CommonBundle.settingsTitle()

    override fun getDescription(): @NlsContexts.Label String  = CommonBundle.settingsTitle()

    override fun getIcon(): Icon?  = AllIcons.General.Settings

    override fun isMyFileType(file: VirtualFile): Boolean {
      return file is SettingsVirtualFile
    }
  }
}


