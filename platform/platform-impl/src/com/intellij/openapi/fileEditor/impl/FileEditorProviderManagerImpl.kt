// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.findByIdOrFromInstance
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SlowOperations.GENERIC
import com.intellij.util.SlowOperations.allowSlowOperations
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.TestOnly

@State(name = "FileEditorProviderManager",
       storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)])
class FileEditorProviderManagerImpl : FileEditorProviderManager,
                                      SerializablePersistentStateComponent<FileEditorProviderManagerImpl.FileEditorProviderManagerState>(FileEditorProviderManagerState()) {
  companion object {
    fun getInstanceImpl(): FileEditorProviderManagerImpl = FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl

    private val LOG = logger<FileEditorProviderManagerImpl>()

    private fun computeKey(providers: List<FileEditorProvider>) = providers.joinToString(separator = ",") { it.editorTypeId }
  }

  @Serializable
  data class FileEditorProviderManagerState(val selectedProviders: Map<String, String> = emptyMap())

  private inline fun doGetProviders(providerChecker: (FileEditorProvider) -> Boolean): List<FileEditorProvider> {
    // collect all possible editors
    val sharedProviders = ArrayList<FileEditorProvider>()
    var hideDefaultEditor = false
    var hasHighPriorityEditors = false
    for (provider in FileEditorProvider.EP_FILE_EDITOR_PROVIDER.extensionList) {
      val result = allowSlowOperations(GENERIC).use {
        providerChecker(provider)
      }

      if (result) {
        sharedProviders.add(provider)
        hideDefaultEditor = hideDefaultEditor or (provider.policy == FileEditorPolicy.HIDE_DEFAULT_EDITOR)
        hasHighPriorityEditors = hasHighPriorityEditors or (provider.policy == FileEditorPolicy.HIDE_OTHER_EDITORS)
        if (provider.policy == FileEditorPolicy.HIDE_DEFAULT_EDITOR && !DumbService.isDumbAware(provider)) {
          val message = "HIDE_DEFAULT_EDITOR is supported only for DumbAware providers; " + provider.javaClass + " is not DumbAware."
          LOG.error(PluginException.createByClass(message, null, provider.javaClass))
        }
      }
    }

    // throw out default editors provider if necessary
    if (hideDefaultEditor) {
      sharedProviders.removeIf { it is DefaultPlatformFileEditorProvider }
    }
    if (hasHighPriorityEditors) {
      sharedProviders.removeIf { it.policy != FileEditorPolicy.HIDE_OTHER_EDITORS }
    }

    // Sort editors according policies
    sharedProviders.sortWith(MyComparator)
    return sharedProviders
  }

  private fun checkProvider(project: Project, file: VirtualFile, provider: FileEditorProvider): Boolean {
    if (DumbService.isDumb(project) && !DumbService.isDumbAware(provider)) {
      return false
    }
    if (!provider.accept(project, file)) {
      return false
    }

    for (suppressor in FileEditorProviderSuppressor.EP_NAME.extensionList) {
      if (suppressor.isSuppressed(project, file, provider)) {
        LOG.info("FileEditorProvider ${provider.javaClass} for VirtualFile $file " +
                 "was suppressed by FileEditorProviderSuppressor ${suppressor.javaClass}")
        return false
      }
    }
    return true
  }

  override fun getProviderList(project: Project, file: VirtualFile): List<FileEditorProvider> {
    return doGetProviders { provider ->
      ReadAction.compute<Boolean, RuntimeException> {
        checkProvider(project, file, provider)
      }
    }
  }

  override suspend fun getProvidersAsync(project: Project, file: VirtualFile): List<FileEditorProvider> {
    return doGetProviders { provider ->
      readAction {
        checkProvider(project, file, provider)
      }
    }
  }

  override fun getProvider(editorTypeId: String): FileEditorProvider? {
    return FileEditorProvider.EP_FILE_EDITOR_PROVIDER.findByIdOrFromInstance(editorTypeId) { it.editorTypeId }
  }

  fun providerSelected(composite: EditorComposite) {
    val providers = composite.allProviders
    if (providers.size < 2) {
      return
    }

    updateState {
      FileEditorProviderManagerState(it.selectedProviders + (computeKey(providers) to composite.selectedWithProvider.provider.editorTypeId))
    }
  }

  fun getSelectedFileEditorProvider(composite: EditorComposite): FileEditorProvider? {
    val editorHistoryManager = EditorHistoryManager.getInstance(composite.project)
    val provider = editorHistoryManager.getSelectedProvider(composite.file)
    val providers = composite.allProviders
    if (provider != null || providers.size < 2) {
      return provider
    }
    return getProvider(state.selectedProviders.get(computeKey(providers)) ?: return null)
  }

  @TestOnly
  fun clearSelectedProviders() {
    updateState {
      FileEditorProviderManagerState()
    }
  }
}

private object MyComparator : Comparator<FileEditorProvider> {
  private fun getWeight(provider: FileEditorProvider): Double {
    return if (provider is WeighedFileEditorProvider) provider.weight else Double.MAX_VALUE
  }

  override fun compare(provider1: FileEditorProvider, provider2: FileEditorProvider): Int {
    val c = provider1.policy.compareTo(provider2.policy)
    if (c != 0) return c
    val value = getWeight(provider1) - getWeight(provider2)
    return if (value > 0) 1 else if (value < 0) -1 else 0
  }
}
