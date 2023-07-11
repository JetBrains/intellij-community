// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.findByIdOrFromInstance
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.TestOnly

private val LOG: Logger
  get() = logger<FileEditorProviderManagerImpl>()

private fun computeKey(providers: List<FileEditorProvider>) = providers.joinToString(separator = ",") { it.editorTypeId }

@Serializable
data class FileEditorProviderManagerState(@JvmField val selectedProviders: Map<String, String> = emptyMap())

@State(name = "FileEditorProviderManager",
       storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)])
class FileEditorProviderManagerImpl : FileEditorProviderManager,
                                      SerializablePersistentStateComponent<FileEditorProviderManagerState>(
                                        FileEditorProviderManagerState()) {

  private fun checkProvider(project: Project,
                            file: VirtualFile,
                            provider: FileEditorProvider,
                            suppressors: List<FileEditorProviderSuppressor>): Boolean {
    if (!DumbService.isDumbAware(provider) && DumbService.isDumb(project)) {
      return false
    }

    if (!provider.accept(project, file)) {
      return false
    }

    for (suppressor in suppressors) {
      if (suppressor.isSuppressed(project, file, provider)) {
        LOG.info("FileEditorProvider ${provider.javaClass} for VirtualFile $file " +
                 "was suppressed by FileEditorProviderSuppressor ${suppressor.javaClass}")
        return false
      }
    }
    return true
  }

  @Suppress("DuplicatedCode")
  override fun getProviderList(project: Project, file: VirtualFile): List<FileEditorProvider> {
    // collect all possible editors
    val sharedProviders = mutableListOf<FileEditorProvider>()
    var hideDefaultEditor = false
    var hasHighPriorityEditors = false

    val suppressors = FileEditorProviderSuppressor.EP_NAME.extensionList
    for (provider in FileEditorProvider.EP_FILE_EDITOR_PROVIDER.extensionList) {
      if (ApplicationManager.getApplication().runReadAction<Boolean, RuntimeException> {
          checkProvider(project = project, file = file, provider = provider, suppressors = suppressors)
        }) {
        sharedProviders.add(provider)
        hideDefaultEditor = hideDefaultEditor or (provider.policy == FileEditorPolicy.HIDE_DEFAULT_EDITOR)
        hasHighPriorityEditors = hasHighPriorityEditors or (provider.policy == FileEditorPolicy.HIDE_OTHER_EDITORS)
        checkPolicy(provider)
      }
    }
    return postProcessResult(hideDefaultEditor = hideDefaultEditor,
                             sharedProviders = sharedProviders,
                             hasHighPriorityEditors = hasHighPriorityEditors)

  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Suppress("DuplicatedCode")
  override suspend fun getProvidersAsync(project: Project, file: VirtualFile): List<FileEditorProvider> {
    // collect all possible editors
    var hideDefaultEditor = false
    var hasHighPriorityEditors = false
    val suppressors = FileEditorProviderSuppressor.EP_NAME.extensionList
    val sharedProviders = coroutineScope {
      FileEditorProvider.EP_FILE_EDITOR_PROVIDER.filterableLazySequence().map { item ->
        async {
          val provider = item.instance ?: return@async null
          if (provider.acceptRequiresReadAction()) {
            if (!readAction {
                checkProvider(project = project, file = file, provider = provider, suppressors = suppressors)
              }) {
              return@async null
            }
          }
          else if (!checkProvider(project = project, file = file, provider = provider, suppressors = suppressors)) {
            return@async null
          }

          hideDefaultEditor = hideDefaultEditor or (provider.policy == FileEditorPolicy.HIDE_DEFAULT_EDITOR)
          hasHighPriorityEditors = hasHighPriorityEditors or (provider.policy == FileEditorPolicy.HIDE_OTHER_EDITORS)
          checkPolicy(provider)
          provider
        }
      }.toList()
    }.mapNotNullTo(mutableListOf()) { it.getCompleted() }
    return postProcessResult(hideDefaultEditor = hideDefaultEditor,
                             sharedProviders = sharedProviders,
                             hasHighPriorityEditors = hasHighPriorityEditors)

  }

  private fun postProcessResult(hideDefaultEditor: Boolean,
                                sharedProviders: MutableList<FileEditorProvider>,
                                hasHighPriorityEditors: Boolean): List<FileEditorProvider> {
    // throw out default editors provider if necessary
    if (hideDefaultEditor) {
      sharedProviders.removeIf { it is DefaultPlatformFileEditorProvider }
    }
    if (hasHighPriorityEditors) {
      sharedProviders.removeIf { it.policy != FileEditorPolicy.HIDE_OTHER_EDITORS }
    }
    // sort editors according policies
    sharedProviders.sortWith(MyComparator)
    return sharedProviders
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
      FileEditorProviderManagerState(
        it.selectedProviders + (computeKey(providers) to composite.selectedWithProvider!!.provider.editorTypeId))
    }
  }

  internal fun getSelectedFileEditorProvider(composite: EditorComposite, project: Project): FileEditorProvider? {
    val provider = EditorHistoryManager.getInstance(project).getSelectedProvider(composite.file)
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

private fun checkPolicy(provider: FileEditorProvider) {
  if (provider.policy == FileEditorPolicy.HIDE_DEFAULT_EDITOR && !DumbService.isDumbAware(provider)) {
    val message = "HIDE_DEFAULT_EDITOR is supported only for DumbAware providers; ${provider.javaClass} is not DumbAware."
    LOG.error(PluginException.createByClass(message, null, provider.javaClass))
  }
}