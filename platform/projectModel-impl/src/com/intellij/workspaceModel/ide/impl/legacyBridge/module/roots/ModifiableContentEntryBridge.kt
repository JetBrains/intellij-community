// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.java.workspace.entities.asJavaResourceRoot
import com.intellij.java.workspace.entities.asJavaSourceRoot
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.CachedValueImpl
import com.intellij.workspaceModel.ide.isEqualOrParentOf
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer

internal class ModifiableContentEntryBridge(
  private val diff: MutableEntityStorage,
  private val modifiableRootModel: ModifiableRootModelBridgeImpl,
  val contentEntryUrl: VirtualFileUrl
) : ContentEntry {
  companion object {
    private val LOG = logger<ModifiableContentEntryBridge>()
  }

  private val virtualFileManager = WorkspaceModel.getInstance(modifiableRootModel.project).getVirtualFileUrlManager()

  private val currentContentEntry = CachedValueImpl {
    val contentEntry = modifiableRootModel.currentModel.contentEntries.firstOrNull { it.url == contentEntryUrl.url } as? ContentEntryBridge
                       ?: error("Unable to find content entry in parent modifiable root model by url: $contentEntryUrl")
    CachedValueProvider.Result.createSingleDependency(contentEntry, modifiableRootModel)
  }

  private fun <P : JpsElement> addSourceFolder(sourceFolderUrl: VirtualFileUrl,
                                               type: JpsModuleSourceRootType<P>,
                                               properties: P,
                                               folderEntitySource: EntitySource): SourceFolder {
    LOG.debugWithTrace { "Add source folder for url: $sourceFolderUrl" }

    if (!contentEntryUrl.isEqualOrParentOf(sourceFolderUrl)) {
      error("Source folder $sourceFolderUrl must be under content entry $contentEntryUrl")
    }

    val duplicate = findDuplicate(sourceFolderUrl, type, properties)
    if (duplicate != null) {
      LOG.debug("Source folder for '$sourceFolderUrl' and type '$type' already exist")
      return duplicate
    }

    val serializer: JpsModuleSourceRootPropertiesSerializer<P> = SourceRootPropertiesHelper.findSerializer(type)
                                                                 ?: error(
                                                                   "Module source root type $type is not registered as JpsModelSerializerExtension")

    val contentRootEntity = currentContentEntry.value.entity
    val sourceRootEntity = SourceRootEntity(url = sourceFolderUrl,
                                            rootTypeId = SourceRootTypeId(serializer.typeId),
                                            entitySource = folderEntitySource)

    SourceRootPropertiesHelper.addPropertiesEntity(sourceRootEntity, properties, serializer)

    diff.modifyContentRootEntity(contentRootEntity) {
      this.sourceRoots += sourceRootEntity
    }


    return currentContentEntry.value.sourceFolders.firstOrNull {
      it.url == sourceFolderUrl.url && it.rootType == type
    } ?: error("Source folder for '$sourceFolderUrl' and type '$type' was not found after adding")
  }

  private fun <P : JpsElement?> findDuplicate(sourceFolderUrl: VirtualFileUrl, type: JpsModuleSourceRootType<P>,
                                              properties: P): SourceFolder? {
    val propertiesFilter: (SourceFolder) -> Boolean = when (properties) {
      is JavaSourceRootProperties -> label@{ sourceFolder: SourceFolder ->
        val javaSourceRoot = (sourceFolder as SourceFolderBridge).sourceRootEntity.asJavaSourceRoot()
        return@label javaSourceRoot != null && javaSourceRoot.generated == properties.isForGeneratedSources
                     && javaSourceRoot.packagePrefix == properties.packagePrefix
      }
      is JavaResourceRootProperties -> label@{ sourceFolder: SourceFolder ->
        val javaResourceRoot = (sourceFolder as SourceFolderBridge).sourceRootEntity.asJavaResourceRoot()
        return@label javaResourceRoot != null && javaResourceRoot.generated == properties.isForGeneratedSources
                     && javaResourceRoot.relativeOutputPath == properties.relativeOutputPath
      }
      else -> { _ -> true }
    }
    return sourceFolders.filter { it.url == sourceFolderUrl.url && it.rootType == type }.find { propertiesFilter.invoke(it) }
  }

  override fun removeSourceFolder(sourceFolder: SourceFolder) {
    LOG.debugWithTrace { "Removing source folder with url: ${sourceFolder.url}" }

    val legacyBridgeSourceFolder = sourceFolder as SourceFolderBridge
    val sourceRootEntity = currentContentEntry.value.sourceRootEntities.firstOrNull { it == legacyBridgeSourceFolder.sourceRootEntity }
    if (sourceRootEntity == null) {
      LOG.error("SourceFolder ${sourceFolder.url} is not present under content entry $contentEntryUrl")
      return
    }
    modifiableRootModel.removeCachedJpsRootProperties(sourceRootEntity.url)
    diff.removeEntity(sourceRootEntity)
  }

  override fun clearSourceFolders() {
    LOG.debugWithTrace { "Clear source folders" }

    currentContentEntry.value.sourceRootEntities.forEach { sourceRoot -> diff.removeEntity(sourceRoot) }
  }

  private fun addExcludeFolder(excludeUrl: VirtualFileUrl, isAutomaticallyImported: Boolean): ExcludeFolder {
    LOG.debugWithTrace { "Add exclude folder for url: ${excludeUrl.url}" }

    if (!contentEntryUrl.isEqualOrParentOf(excludeUrl)) {
      error("Exclude folder $excludeUrl must be under content entry $contentEntryUrl")
    }

    if (excludeUrl !in currentContentEntry.value.entity.excludedUrls.map { it.url }) {
      updateContentEntry {
        val source = if (!isAutomaticallyImported) getInternalFileSource(entitySource) ?: entitySource else entitySource
        excludedUrls = excludedUrls + ExcludeUrlEntity(excludeUrl, source)
      }
    }

    return currentContentEntry.value.excludeFolders.firstOrNull {
      it.url == excludeUrl.url
    } ?: error("Exclude folder $excludeUrl must be present after adding it to content entry $contentEntryUrl")
  }

  override fun addExcludeFolder(file: VirtualFile): ExcludeFolder = addExcludeFolder(file.toVirtualFileUrl(virtualFileManager), false)
  override fun addExcludeFolder(url: String): ExcludeFolder = addExcludeFolder(virtualFileManager.getOrCreateFromUrl(url), false)

  override fun addExcludeFolder(url: String, isAutomaticallyImported: Boolean): ExcludeFolder {
    return addExcludeFolder(virtualFileManager.getOrCreateFromUrl(url), isAutomaticallyImported)
  }

  override fun removeExcludeFolder(excludeFolder: ExcludeFolder) {
    LOG.debugWithTrace { "Remove exclude folder for folder: ${excludeFolder.url}." }

    val virtualFileUrl = (excludeFolder as ExcludeFolderBridge).excludeFolderUrl

    val excludeUrlEntities = currentContentEntry.value.entity.excludedUrls.filter { it.url == virtualFileUrl }
    if (excludeUrlEntities.isEmpty()) {
      error("Exclude folder ${excludeFolder.url} is not under content entry $contentEntryUrl")
    }
    excludeUrlEntities.forEach {
      diff.removeEntity(it)
    }
  }

  private fun updateContentEntry(updater: ContentRootEntity.Builder.() -> Unit) {
    diff.modifyContentRootEntity(currentContentEntry.value.entity, updater)
  }

  override fun removeExcludeFolder(url: String): Boolean {
    LOG.debugWithTrace { "Remove exclude folder for url: $url." }

    val virtualFileUrl = virtualFileManager.getOrCreateFromUrl(url)

    val excludedUrls = currentContentEntry.value.entity.excludedUrls.map { it.url }
    if (!excludedUrls.contains(virtualFileUrl)) return false

    val contentRootEntity = currentContentEntry.value.entity
    val (_, toRemove) = contentRootEntity.excludedUrls.partition { excludedUrl -> excludedUrl.url != virtualFileUrl }
    toRemove.forEach { diff.removeEntity(it) }

    return true
  }

  override fun clearExcludeFolders() {
    LOG.debugWithTrace { "Clear exclude folders." }

    updateContentEntry {
      excludedUrls = mutableListOf()
    }
  }

  override fun addExcludePattern(pattern: String) {
    LOG.debugWithTrace { "Add exclude pattern: $pattern" }

    updateContentEntry {
      if (!excludedPatterns.contains(pattern)) excludedPatterns.add(pattern)
    }
  }

  override fun removeExcludePattern(pattern: String) {
    LOG.debugWithTrace { "Remove exclude pattern: $pattern" }

    updateContentEntry {
      excludedPatterns.remove(pattern)
    }
  }

  override fun setExcludePatterns(patterns: MutableList<String>) {
    LOG.debugWithTrace { "Set exclude patterns" }

    updateContentEntry {
      excludedPatterns = patterns.toMutableList()
    }
  }

  override fun equals(other: Any?): Boolean {
    return (other as? ContentEntry)?.url == url
  }

  override fun hashCode(): Int {
    return url.hashCode()
  }

  override fun addSourceFolder(file: VirtualFile, isTestSource: Boolean) = addSourceFolder(file, isTestSource, "")

  override fun addSourceFolder(file: VirtualFile, isTestSource: Boolean, packagePrefix: String): SourceFolder =
    addSourceFolder(file, if (isTestSource) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE,
                                                      JavaSourceRootProperties(packagePrefix, false))

  override fun <P : JpsElement> addSourceFolder(file: VirtualFile, type: JpsModuleSourceRootType<P>): SourceFolder =
    addSourceFolder(file, type, type.createDefaultProperties())

  override fun addSourceFolder(url: String, isTestSource: Boolean): SourceFolder =
    addSourceFolder(url, if (isTestSource) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE)

  override fun <P : JpsElement> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>): SourceFolder {
    return addSourceFolder(url, type, type.createDefaultProperties())
  }

  override fun <P : JpsElement> addSourceFolder(url: String,
                                                 type: JpsModuleSourceRootType<P>,
                                                 externalSource: ProjectModelExternalSource): SourceFolder {
    return addSourceFolder(url, type, type.createDefaultProperties(), externalSource)
  }

  override fun <P : JpsElement> addSourceFolder(url: String,
                                                type: JpsModuleSourceRootType<P>,
                                                isAutomaticallyImported: Boolean): SourceFolder {
    val contentRootSource = currentContentEntry.value.entity.entitySource
    val source = if (isAutomaticallyImported) contentRootSource else getInternalFileSource(contentRootSource) ?: contentRootSource
    return addSourceFolder(virtualFileManager.getOrCreateFromUrl(url), type, type.createDefaultProperties(), source)
  }

  override fun <P : JpsElement> addSourceFolder(file: VirtualFile, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder {
    val contentRootSource = currentContentEntry.value.entity.entitySource
    val source: EntitySource = getInternalFileSource(contentRootSource) ?: contentRootSource
    return addSourceFolder(file.toVirtualFileUrl(virtualFileManager), type, properties, source)
  }

  override fun <P : JpsElement> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder {
    val contentRootSource = currentContentEntry.value.entity.entitySource
    val source: EntitySource = getInternalFileSource(contentRootSource) ?: contentRootSource
    return addSourceFolder(virtualFileManager.getOrCreateFromUrl(url), type, properties, source)
  }

  override fun <P : JpsElement> addSourceFolder(url: String,
                                                type: JpsModuleSourceRootType<P>,
                                                properties: P,
                                                isAutomaticallyImported: Boolean): SourceFolder {
    val contentRootSource = currentContentEntry.value.entity.entitySource
    val source = if (isAutomaticallyImported) contentRootSource else getInternalFileSource(contentRootSource) ?: contentRootSource
    return addSourceFolder(virtualFileManager.getOrCreateFromUrl(url), type, properties, source)
  }

  override fun <P : JpsElement> addSourceFolder(url: String,
                                                type: JpsModuleSourceRootType<P>,
                                                properties: P,
                                                externalSource: ProjectModelExternalSource?): SourceFolder {
    val contentRootSource = currentContentEntry.value.entity.entitySource
    val source = if (externalSource != null) contentRootSource else getInternalFileSource(contentRootSource) ?: contentRootSource
    return addSourceFolder(virtualFileManager.getOrCreateFromUrl(url), type, properties, source)
  }

  override fun getFile(): VirtualFile? = currentContentEntry.value.file
  override fun getUrl(): String = contentEntryUrl.url
  override fun getSourceFolders(): Array<SourceFolder> = currentContentEntry.value.sourceFolders
  override fun getSourceFolders(rootType: JpsModuleSourceRootType<*>): List<SourceFolder> =
    currentContentEntry.value.getSourceFolders(rootType)

  override fun getSourceFolders(rootTypes: MutableSet<out JpsModuleSourceRootType<*>>): List<SourceFolder> =
    currentContentEntry.value.getSourceFolders(rootTypes)

  override fun getSourceFolderFiles(): Array<VirtualFile> = currentContentEntry.value.sourceFolderFiles
  override fun getExcludeFolders(): Array<ExcludeFolder> = currentContentEntry.value.excludeFolders
  override fun getExcludeFolderUrls(): MutableList<String> = currentContentEntry.value.excludeFolderUrls
  override fun getExcludeFolderFiles(): Array<VirtualFile> = currentContentEntry.value.excludeFolderFiles
  override fun getExcludePatterns(): List<String> = currentContentEntry.value.excludePatterns

  override fun getRootModel(): ModuleRootModel = modifiableRootModel
  override fun isSynthetic(): Boolean = currentContentEntry.value.isSynthetic
}

/**
 * Print a debug message and add a stack trace if trace logging is enabled
 */
private fun Logger.debugWithTrace(msg: () -> String) {
  val e = if (this.isTraceEnabled) RuntimeException("Stack trace of the log entry:") else null
  this.debug(e, msg)
}
