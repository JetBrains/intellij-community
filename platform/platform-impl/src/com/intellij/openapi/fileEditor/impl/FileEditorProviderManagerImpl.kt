// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.LazyExtension
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SlowOperations
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.seconds

private val LOG: Logger
  get() = logger<FileEditorProviderManagerImpl>()

private fun computeKey(providers: List<FileEditorWithProvider>) = providers.joinToString(separator = ",") { it.provider.editorTypeId }

@ApiStatus.Internal
@Serializable
data class FileEditorProviderManagerState(@JvmField val selectedProviders: Map<String, String> = emptyMap())

@ApiStatus.Internal
@State(name = "FileEditorProviderManager",
       storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)])
class FileEditorProviderManagerImpl
  : FileEditorProviderManager, SerializablePersistentStateComponent<FileEditorProviderManagerState>(FileEditorProviderManagerState()) {
  @Suppress("DuplicatedCode")
  override fun getProviderList(project: Project, file: VirtualFile): List<FileEditorProvider> {
    // collect all possible editors
    val sharedProviders = mutableListOf<FileEditorProvider>()

    val suppressors = FileEditorProviderSuppressor.EP_NAME.extensionList
    val hasDocument by lazy {
      ApplicationManager.getApplication().runReadAction<Boolean, RuntimeException> {
        FileDocumentManager.getInstance().getDocument(file) != null
      }
    }

    val fileType = file.fileType
    for (item in FileEditorProvider.EP_FILE_EDITOR_PROVIDER.filterableLazySequence()) {
      if (!isAcceptedByFileType(item = item, fileType = fileType, file = file) || (item.isDocumentRequired && !hasDocument)) {
        continue
      }

      val provider = item.instance ?: continue
      if (!DumbService.getInstance(project).isUsableInCurrentContext(provider)) {
        continue
      }

      if (ApplicationManager.getApplication().runReadAction<Boolean, RuntimeException> {
          SlowOperations.knownIssue("IDEA-307300, EA-816241").use {
            checkProvider(project = project, file = file, provider = provider, suppressors = suppressors)
          }
        }) {
        sharedProviders.add(provider)
      }
    }
    return postProcessResult(sharedProviders)
  }

  @Suppress("DuplicatedCode")
  override suspend fun getProvidersAsync(project: Project, file: VirtualFile): List<FileEditorProvider> {
    return getProviders(project, file, dumUnawareOnly = false, excludeIds = emptySet())
  }

  override suspend fun getDumbUnawareProviders(project: Project, file: VirtualFile, excludeIds: Set<String>): List<FileEditorProvider> {
    return getProviders(project = project, file = file, dumUnawareOnly = true, excludeIds = excludeIds)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Suppress("DuplicatedCode")
  private suspend fun getProviders(
    project: Project,
    file: VirtualFile,
    dumUnawareOnly: Boolean,
    excludeIds: Set<String>,
  ): List<FileEditorProvider> {
    // collect all possible editors
    val suppressors = FileEditorProviderSuppressor.EP_NAME.extensionList

    // Not lazy - avoid thread starvation.
    // We run in parallel, and each provider can get blocked while getting the file type (e.g., during TextMate bundle initialization).

    val sharedProviders = coroutineScope {
      val fileType = async {
        blockingContext { file.fileType }
      }

      var hasDocument: Boolean? = null

      FileEditorProvider.EP_FILE_EDITOR_PROVIDER.filterableLazySequence().mapNotNull { item ->
        if (excludeIds.contains(item.id)) {
          return@mapNotNull null
        }

        async {
          if (dumUnawareOnly) {
            val aClass = item.implementationClass
            if (aClass != null && DumbAware::class.java.isAssignableFrom(aClass)) {
              return@async null
            }
          }

          if (!isAcceptedByFileType(item = item, fileType = fileType, file = file)) {
            return@async null
          }

          if (item.isDocumentRequired) {
            if (hasDocument == null) {
              val fileDocumentManager = serviceAsync<FileDocumentManager>()
              hasDocument = readAction { fileDocumentManager.getDocument(file) != null }
            }

            if (hasDocument == false) {
              return@async null
            }
          }

          try {
            withTimeout(30.seconds) {
              val provider = item.instance ?: return@withTimeout null
              if (excludeIds.contains(provider.editorTypeId)) {
                return@withTimeout null
              }

              getProviderIfApplicable(
                provider = provider,
                project = project,
                file = file,
                suppressors = suppressors,
                pluginDescriptor = item.pluginDescriptor,
              )?.takeIf { !dumUnawareOnly || !DumbService.isDumbAware(it) }
            }
          }
          catch (e: TimeoutCancellationException) {
            LOG.error(PluginException("Cannot check provider ${item.implementationClassName}", e, item.pluginDescriptor.pluginId))
            null
          }
        }
      }.toList()
    }.mapNotNullTo(mutableListOf()) { it.getCompleted() }
    return postProcessResult(sharedProviders)
  }

  private fun postProcessResult(sharedProviders: MutableList<FileEditorProvider>): List<FileEditorProvider> {
    var hideDefaultEditor = false
    var hasHighPriorityEditors = false
    for (provider in sharedProviders) {
      hideDefaultEditor = hideDefaultEditor or (provider.policy == FileEditorPolicy.HIDE_DEFAULT_EDITOR)
      hasHighPriorityEditors = hasHighPriorityEditors or (provider.policy == FileEditorPolicy.HIDE_OTHER_EDITORS)
      checkPolicy(provider)
    }

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
    val list = composite.allEditorsWithProviders
    if (list.size < 2) {
      return
    }

    updateState {
      FileEditorProviderManagerState(it.selectedProviders + (computeKey(list) to composite.selectedWithProvider!!.provider.editorTypeId))
    }
  }

  internal fun getSelectedFileEditorProvider(composite: EditorComposite, project: Project): FileEditorProvider? {
    return getSelectedFileEditorProvider(
      file = composite.file,
      fileEditorWithProviders = composite.allEditorsWithProviders,
      editorHistoryManager = EditorHistoryManager.getInstance(project),
    )
  }

  internal fun getSelectedFileEditorProvider(
    file: VirtualFile,
    fileEditorWithProviders: List<FileEditorWithProvider>,
    editorHistoryManager: EditorHistoryManager,
  ): FileEditorProvider? {
    val provider = editorHistoryManager.getSelectedProvider(file)
    if (provider != null || fileEditorWithProviders.size < 2) {
      return provider
    }
    return getProvider(state.selectedProviders.get(computeKey(fileEditorWithProviders)) ?: return null)
  }

  @TestOnly
  fun clearSelectedProviders() {
    updateState {
      FileEditorProviderManagerState()
    }
  }
}

private suspend fun getProviderIfApplicable(
  provider: FileEditorProvider,
  project: Project,
  file: VirtualFile,
  suppressors: List<FileEditorProviderSuppressor>,
  pluginDescriptor: PluginDescriptor,
): FileEditorProvider? {
  if (!DumbService.getInstance(project).isUsableInCurrentContext(provider)) {
    LOG.warn("Please make ${provider.javaClass} dumb-aware")
    return null
  }

  try {
    if (provider.acceptRequiresReadAction()) {
      if (!readAction {
          checkProvider(project = project, file = file, provider = provider, suppressors = suppressors)
        }) {
        return null
      }
    }
    else {
      //println("TO CHECK: " + item.implementationClassName)
      if (!checkProvider(project = project, file = file, provider = provider, suppressors = suppressors)) {
        //println("FAIL: " + item.implementationClassName)
        return null
      }
      //println("PASS: " + item.implementationClassName)
    }
    return provider
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.error(PluginException(e, pluginDescriptor.pluginId))
    return null
  }
}

private fun checkProvider(project: Project,
                          file: VirtualFile,
                          provider: FileEditorProvider,
                          suppressors: List<FileEditorProviderSuppressor>): Boolean {
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

private val LazyExtension<FileEditorProvider>.isDocumentRequired
  get() = getCustomAttribute("isDocumentRequired").toBoolean()

private val LazyExtension<FileEditorProvider>.fileType
  get() = getCustomAttribute("fileType")

private suspend fun isAcceptedByFileType(item: LazyExtension<FileEditorProvider>, fileType: Deferred<FileType>, file: VirtualFile): Boolean {
  val providerFileTypeName = item.fileType
  // VcsLogFileType is not registered in FileTypeRegistry - we should check also by name
  if (providerFileTypeName != null && fileType.await().name != providerFileTypeName) {
    val fileTypeRegistry = FileTypeRegistry.getInstance()
    val providerFileType = fileTypeRegistry.findFileTypeByName(providerFileTypeName)
    if (providerFileType == null || !fileTypeRegistry.isFileOfType(file, providerFileType)) {
      return false
    }
  }
  return true
}

private fun isAcceptedByFileType(item: LazyExtension<FileEditorProvider>, fileType: FileType, file: VirtualFile): Boolean {
  val providerFileTypeName = item.fileType
  // VcsLogFileType is not registered in FileTypeRegistry - we should check also by name
  if (providerFileTypeName != null && fileType.name != providerFileTypeName) {
    val fileTypeRegistry = FileTypeRegistry.getInstance()
    val providerFileType = fileTypeRegistry.findFileTypeByName(providerFileTypeName)
    if (providerFileType == null || !fileTypeRegistry.isFileOfType(file, providerFileType)) {
      return false
    }
  }
  return true
}

private fun checkPolicy(provider: FileEditorProvider) {
  if (provider.policy == FileEditorPolicy.HIDE_DEFAULT_EDITOR && !DumbService.isDumbAware(provider)) {
    val message = "HIDE_DEFAULT_EDITOR is supported only for DumbAware providers; ${provider.javaClass} is not DumbAware."
    LOG.error(PluginException.createByClass(message, null, provider.javaClass))
  }
}