// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.options.newEditor.settings.SettingsVirtualFileHolder.SettingsVirtualFile
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon

@ApiStatus.Internal
@Service(Level.PROJECT)
internal class SettingsVirtualFileHolder private constructor(private val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): SettingsVirtualFileHolder {
      return project.getService(SettingsVirtualFileHolder::class.java)
    }
  }

  private val settingsFileRef = AtomicReference<SettingsVirtualFile?>(null)

  suspend fun getOrCreate(initializer: () -> SettingsDialog): SettingsVirtualFile {
    return withContext(Dispatchers.EDT) {
      val settingsVirtualFile = settingsFileRef.get()

      if (settingsVirtualFile != null) {
        return@withContext settingsVirtualFile
      }
      val settingsDialog = initializer.invoke()
      val newVirtualFile = SettingsVirtualFile(settingsDialog, project)
      Disposer.register(settingsDialog.disposable, Disposable {
        val fileEditorManager = FileEditorManager.getInstance(newVirtualFile.project) as FileEditorManagerEx;
        fileEditorManager.closeFile(newVirtualFile)
        settingsFileRef.set(null)
      })
      settingsFileRef.compareAndSet(null, newVirtualFile)
      return@withContext newVirtualFile
    }
  }

  internal fun virtualFileExists() = settingsFileRef.get() != null

  fun invalidate(): SettingsVirtualFile? {
    return settingsFileRef.getAndSet(null)
  }

  class SettingsVirtualFile(val dialog: SettingsDialog, val project: Project) :
    LightVirtualFile(CommonBundle.settingsTitle(), SettingFileType(), ""), OptionallyIncluded {

    init {
      putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    }

    override fun isIncludedInEditorHistory(project: Project): Boolean = false

    override fun shouldSkipEventSystem() = true
  }

  private class SettingFileType : FakeFileType() {
    override fun getName(): @NonNls String = CommonBundle.settingsTitle()

    override fun getDescription(): @NlsContexts.Label String = CommonBundle.settingsTitle()

    override fun getIcon(): Icon? = AllIcons.General.Settings

    override fun isMyFileType(file: VirtualFile): Boolean {
      return file is SettingsVirtualFile
    }
  }
}

private class CloseSettingsAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: run {
      e.presentation.isEnabled = false
      return
    }
    e.presentation.isEnabled = SettingsVirtualFileHolder.getInstance(project).virtualFileExists()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val fileEditor = (PlatformDataKeys.FILE_EDITOR.getData(e.dataContext)
                      ?: PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.getData(e.dataContext)) as? SettingsFileEditor ?: return
    val settingsVirtualFile = fileEditor.file as? SettingsVirtualFile ?: return
    settingsVirtualFile.dialog.doCancelAction(e.inputEvent)
  }
}