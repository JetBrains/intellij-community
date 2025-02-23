// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.newEditor.OptionsEditorColleague
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.options.newEditor.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

  suspend fun getOrCreate(toSelect: Configurable?, initializer: () -> SettingsDialog): SettingsVirtualFile {
    return withContext(Dispatchers.EDT) {
      val settingsVirtualFile = settingsFileRef.get()

      if (settingsVirtualFile != null) {
        val editor = settingsVirtualFile.getOrCreateDialog().editor as? SettingsEditor
        if (editor != null && toSelect != null) {
          editor.select(toSelect)
        }
        return@withContext settingsVirtualFile
      }
      val newVirtualFile = SettingsVirtualFile(project, initializer)
      settingsFileRef.compareAndSet(null, newVirtualFile)
      return@withContext newVirtualFile
    }
  }

  internal fun virtualFileExists() = settingsFileRef.get() != null

  fun invalidate(): SettingsVirtualFile? {
    return settingsFileRef.getAndSet(null)
  }

  class SettingsVirtualFile(val project: Project, private val initializer: () -> SettingsDialog) :
    LightVirtualFile(CommonBundle.settingsTitle(), SettingsFileType, ""), OptionallyIncluded {
    private val wasModified = AtomicBoolean()

    private val dialogLazy = SynchronizedClearableLazy {
      val dialog = initializer()
      Disposer.register(dialog.disposable, Disposable {
        val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx;
        fileEditorManager.closeFile(this)
        wasModified.set(false)
        val manager = project.getServiceIfCreated(FileStatusManager::class.java)
        manager?.fileStatusChanged(this@SettingsVirtualFile)
      })

      val settingsEditor = dialog.editor as? SettingsEditor
      settingsEditor?.addOptionsListener(object : OptionsEditorColleague.Adapter() {
        override fun onModifiedAdded(configurable: Configurable?): Promise<in Any> {
          updateIsModified()
          return resolvedPromise()
        }

        override fun onModifiedRemoved(configurable: Configurable?): Promise<in Any> {
          updateIsModified()
          return resolvedPromise()
        }

        private fun updateIsModified() {
          val modified = settingsEditor.isModified
          if (wasModified.get() != modified) {
            wasModified.set(modified)
            val manager = project.getServiceIfCreated(FileStatusManager::class.java)
            manager?.fileStatusChanged(this@SettingsVirtualFile)
          }
        }
      })

      dialog
    }

    init {
      putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
      putUserData(FileDocumentManagerBase.TRACK_NON_PHYSICAL, true)
    }

    fun getOrCreateDialog(): SettingsDialog = dialogLazy.value

    fun isModified(): Boolean {
      val dialog = dialogLazy.valueIfInitialized ?: return false
      val settingsEditor = dialog.editor as? SettingsEditor ?: return false
      return settingsEditor.isModified
    }

    override fun isIncludedInEditorHistory(project: Project): Boolean = true
    override fun isPersistedInEditorHistory() = false

    override fun shouldSkipEventSystem() = true

    fun disposeDialog() {
      dialogLazy.valueIfInitialized?.apply {
        Disposer.dispose( this.disposable )
      }
      dialogLazy.drop()
    }

    fun getConfigurableId(): String? {
      val dialog = dialogLazy.valueIfInitialized ?: return null
      val settingsEditor = dialog.editor as? SettingsEditor ?: return null
      return settingsEditor.selectedConfigurableId
    }

    fun setConfigurableId(configurableId: String?) {
      if (configurableId == null) {
        return
      }
      val dialog = dialogLazy.valueIfInitialized ?: return
      val settingsEditor = dialog.editor as? SettingsEditor ?: return

      val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, /* withIdeSettings = */true)
        .takeIf { !it.configurables.isEmpty() }
      val configurableToSelect = ConfigurableVisitor.findById(configurableId, listOf(group)) ?: return
      settingsEditor.setNavigatingNow();
      settingsEditor.select(configurableToSelect)
    }
  }

  private object SettingsFileType : FileType {
    override fun getName(): @NonNls String = CommonBundle.settingsTitle()

    override fun getDescription(): @NlsContexts.Label String = CommonBundle.settingsTitle()
    override fun getDefaultExtension() = ""

    override fun getIcon() = AllIcons.General.Settings
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = true
  }
}