// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.newEditor.OptionsEditorColleague
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.options.newEditor.SettingsDialogListener
import com.intellij.openapi.options.newEditor.SettingsEditor
import com.intellij.openapi.options.newEditor.settings.SettingsVirtualFileHolder.SettingsVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.UIBundle
import com.intellij.util.application
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import javax.swing.JComponent

@ApiStatus.Internal
@Service(Level.PROJECT)
internal class SettingsVirtualFileHolder private constructor(private val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): SettingsVirtualFileHolder {
      return project.getService(SettingsVirtualFileHolder::class.java)
    }

    fun getInstanceIfExists(project: Project): SettingsVirtualFileHolder? {
      return project.getServiceIfCreated(SettingsVirtualFileHolder::class.java)
    }
  }

  private val settingsFileRef = AtomicReference<SettingsVirtualFile?>(null)

  fun getIfExists(): SettingsVirtualFile? {
    return settingsFileRef.get()
  }

  suspend fun getOrCreate(toSelect: Configurable?, initializer: () -> SettingsDialog): SettingsVirtualFile {
    return withContext(Dispatchers.EDT) {
      val settingsVirtualFile = settingsFileRef.get()

      if (settingsVirtualFile != null) {
        val dialogIfCreated = settingsVirtualFile.getIfCreated()
        if (dialogIfCreated != null) {
          if (toSelect != null) {
            (dialogIfCreated.editor as? SettingsEditor)?.select(toSelect)
          }
        } else {
          settingsVirtualFile.initializer = initializer
        }
        return@withContext settingsVirtualFile
      }
      val newVirtualFile = SettingsVirtualFile(project, initializer)
      settingsFileRef.compareAndSet(null, newVirtualFile)
      return@withContext newVirtualFile
    }
  }

  internal fun getVirtualFileIfExists() = settingsFileRef.get()

  fun invalidate(): SettingsVirtualFile? {
    return settingsFileRef.getAndSet(null)
  }

  class SettingsVirtualFile(val project: Project, internal var initializer: () -> SettingsDialog) :
    LightVirtualFile(settingsFileName(project), SettingsFileType, ""), OptionallyIncluded {
    private val wasModified = AtomicBoolean()

    private val dialogLazy = SynchronizedClearableLazy {
      val dialog = initializer()
      val disposable = Disposable {
        val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
        fileEditorManager.closeFile(this)
        wasModified.set(false)
        val manager = project.getServiceIfCreated(FileStatusManager::class.java)
        manager?.fileStatusChanged(this@SettingsVirtualFile)
      }
      Disposer.register(dialog.disposable, disposable)

      val settingsEditor = dialog.editor as? SettingsEditor ?: return@SynchronizedClearableLazy dialog
      settingsEditor.addOptionsListener(object : OptionsEditorColleague.Adapter() {
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

      val connection = application.messageBus.connect(disposable)
      connection.subscribe(SettingsDialogListener.TOPIC, object : SettingsDialogListener {
        override fun afterApply(editor: SettingsEditor, modifiedConfigurableIds: Set<String>) {
          if (editor != settingsEditor) {
            settingsEditor.putUserData(KEY, modifiedConfigurableIds)
            EditorNotifications.getInstance(project).updateNotifications(this@SettingsVirtualFile)
          }
        }
      })

      dialog
    }

    init {
      putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
      putUserData(FileDocumentManagerBase.TRACK_NON_PHYSICAL, true)
    }

    override fun getPresentableUrl(): @NlsSafe String = "$SETTINGS_KEY://${CommonBundle.settingsTitle()}"

    override fun getPresentableName() = CommonBundle.settingsTitle()

    fun getOrCreateDialog(): SettingsDialog = dialogLazy.value

    fun getIfCreated(): SettingsDialog? = dialogLazy.valueIfInitialized

    fun isModified(): Boolean {
      val dialog = dialogLazy.valueIfInitialized ?: return false
      val settingsEditor = dialog.editor as? SettingsEditor ?: return false
      return settingsEditor.isModified
    }

    override fun isIncludedInEditorHistory(project: Project): Boolean = true
    override fun isPersistedInEditorHistory() = false

    override fun shouldSkipEventSystem() = true

    override fun getFileSystem(): VirtualFileSystem {
      return SettingsFileSystem.getInstance() ?: super.getFileSystem()
    }

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
      settingsEditor.setNavigatingNow()
      settingsEditor.select(configurableToSelect)
    }
  }

  private object SettingsFileType : FileType {
    override fun getName(): @NonNls String = "settingsType"

    override fun getDescription(): @NlsContexts.Label String = "SettingsFile"
    override fun getDefaultExtension() = ""

    override fun getIcon() = AllIcons.General.Settings
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = true
  }
}

private class SettingModifiedExternallyNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (file !is SettingsVirtualFile)
      return null
    return Function {
      val settingsEditor = file.getOrCreateDialog().editor as SettingsEditor? ?: return@Function null
      val userData: Set<String> = settingsEditor.getUserData(KEY) ?: return@Function null

      val selectedConfigurableId = settingsEditor.selectedConfigurableId ?: return@Function null
      if (!userData.contains(selectedConfigurableId)) {
        return@Function null
      }
      settingsEditor.editor.putUserData(KEY, Collections.singleton(selectedConfigurableId))

      val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Info)
      panel.text = UIBundle.message("settings.tab.modified.externally.text")
      panel.createActionLabel(UIBundle.message("settings.tab.modified.externally.action.text"), Runnable {
        settingsEditor.putUserData(KEY, null)
        settingsEditor.editor.configurable?.reset()
        EditorNotifications.getInstance(project).updateNotifications(file)
      })
      return@Function panel
    }
  }
}
private fun settingsFileName(project: Project): String {
  return "${project.locationHash}/$SETTINGS_KEY"
}

private class SettingsFileSystem : DummyFileSystem() {

  companion object{
    const val PROTOCOL = SETTINGS_KEY

    fun getInstance(): SettingsFileSystem? {
      return VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as SettingsFileSystem?
    }
  }

  override fun findFileByPath(path: String): VirtualFile? {
    val split = path.split("/").filterNot(String::isNullOrBlank)
    return ProjectManager.getInstance().openProjects.firstOrNull {
      it.locationHash == split.getOrNull(0)
    }?.let { project ->
      SettingsVirtualFileHolder.getInstance(project).getIfExists() ?: return null
    }
  }

  override fun getProtocol(): String {
    return PROTOCOL
  }
}

private class SettingsNavBarModelExtension: AbstractNavBarModelExtension() {
  override fun getPresentableText(obj: Any?): String? {
    val virtualFile = PsiUtilCore.getVirtualFile(obj as? PsiElement ?: return null) ?: return null
    if (virtualFile is SettingsVirtualFile)
      return CommonBundle.settingsTitle()
    else
      return null
  }
}

private const val SETTINGS_KEY = "settings"

private val KEY = Key.create<Set<String>>("SettingModifiedExternally")