// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.CachedValueImpl
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import com.intellij.workspaceModel.ide.isEqualOrParentOf
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addSourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ExcludeUrlEntity
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity
import com.intellij.workspaceModel.storage.bridgeEntities.asJavaResourceRoot
import com.intellij.workspaceModel.storage.bridgeEntities.asJavaSourceRoot
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
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

  private val virtualFileManager = VirtualFileUrlManager.getInstance(modifiableRootModel.project)

  private val currentContentEntry = CachedValueImpl {
    val contentEntry = modifiableRootModel.currentModel.contentEntries.firstOrNull { it.url == contentEntryUrl.url } as? ContentEntryBridge
                       ?: error("Unable to find content entry in parent modifiable root model by url: $contentEntryUrl")
    CachedValueProvider.Result.createSingleDependency(contentEntry, modifiableRootModel)
  }

  private fun <P : JpsElement> addSourceFolder(sourceFolderUrl: VirtualFileUrl,
                                               type: JpsModuleSourceRootType<P>,
                                               properties: P,
                                               folderEntitySource: EntitySource): SourceFolder {
    if (!contentEntryUrl.isEqualOrParentOf(sourceFolderUrl)) {
      error("Source folder $sourceFolderUrl must be under content entry $contentEntryUrl")
    }

    val duplicate = findDuplicate(sourceFolderUrl, type, properties)
    if (duplicate != null) {
      LOG.debug("Source folder for '$sourceFolderUrl' and type '$type' already exist")
      return duplicate
    }

    val serializer: JpsModuleSourceRootPropertiesSerializer<P> = SourceRootPropertiesHelper.findSerializer(type)
                                                                 ?: error("Module source root type $type is not registered as JpsModelSerializerExtension")

    val contentRootEntity = currentContentEntry.value.entity
    val sourceRootEntity = diff.addSourceRootEntity(
      contentRoot = contentRootEntity,
      url = sourceFolderUrl,
      rootType = serializer.typeId,
      source = folderEntitySource
    )

    SourceRootPropertiesHelper.addPropertiesEntity(diff, sourceRootEntity, properties, serializer)

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
    currentContentEntry.value.sourceRootEntities.forEach { sourceRoot -> diff.removeEntity(sourceRoot) }
  }

  private fun addExcludeFolder(excludeUrl: VirtualFileUrl, projectSource: ProjectModelExternalSource?): ExcludeFolder {
    if (!contentEntryUrl.isEqualOrParentOf(excludeUrl)) {
      error("Exclude folder $excludeUrl must be under content entry $contentEntryUrl")
    }

    if (excludeUrl !in currentContentEntry.value.entity.excludedUrls.map { it.url }) {
      updateContentEntry {
        val source = if (projectSource == null) getInternalFileSource(entitySource) ?: entitySource else entitySource
        excludedUrls = excludedUrls + ExcludeUrlEntity(excludeUrl, source)
      }
    }

    return currentContentEntry.value.excludeFolders.firstOrNull {
      it.url == excludeUrl.url
    } ?: error("Exclude folder $excludeUrl must be present after adding it to content entry $contentEntryUrl")
  }

  override fun addExcludeFolder(file: VirtualFile): ExcludeFolder = addExcludeFolder(file.toVirtualFileUrl(virtualFileManager), null)
  override fun addExcludeFolder(url: String): ExcludeFolder = addExcludeFolder(virtualFileManager.fromUrl(url), null)
  override fun addExcludeFolder(url: String, source: ProjectModelExternalSource): ExcludeFolder {
    return addExcludeFolder(virtualFileManager.fromUrl(url), source)
  }

  override fun removeExcludeFolder(excludeFolder: ExcludeFolder) {
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
    diff.modifyEntity(currentContentEntry.value.entity, updater)
  }

  override fun removeExcludeFolder(url: String): Boolean {
    val virtualFileUrl = virtualFileManager.fromUrl(url)

    val excludedUrls = currentContentEntry.value.entity.excludedUrls.map { it.url }
    if (!excludedUrls.contains(virtualFileUrl)) return false

    val contentRootEntity = currentContentEntry.value.entity
    val (new, toRemove) = contentRootEntity.excludedUrls.partition {excludedUrl -> excludedUrl.url != virtualFileUrl  }
    updateContentEntry {
      this.excludedUrls = new
    }
    toRemove.forEach { diff.removeEntity(it) }

    return true
  }

  override fun clearExcludeFolders() {
    updateContentEntry {
      excludedUrls = mutableListOf()
    }
  }

  override fun addExcludePattern(pattern: String) {
    updateContentEntry {
      if (!excludedPatterns.contains(pattern)) excludedPatterns.add(pattern)
    }
  }

  override fun removeExcludePattern(pattern: String) {
    updateContentEntry {
      excludedPatterns.remove(pattern)
    }
  }

  override fun setExcludePatterns(patterns: MutableList<String>) {
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
                                                useSourceOfContentRoot: Boolean): SourceFolder {
    val contentRootSource = currentContentEntry.value.entity.entitySource
    val source = if (useSourceOfContentRoot) contentRootSource else getInternalFileSource(contentRootSource) ?: contentRootSource
    return addSourceFolder(virtualFileManager.fromUrl(url), type, type.createDefaultProperties(), source)
  }

  override fun <P : JpsElement> addSourceFolder(file: VirtualFile, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder {
    val contentRootSource = currentContentEntry.value.entity.entitySource
    val source: EntitySource = getInternalFileSource(contentRootSource) ?: contentRootSource
    return addSourceFolder(file.toVirtualFileUrl(virtualFileManager), type, properties, source)
  }

  override fun <P : JpsElement> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder {
    val contentRootSource = currentContentEntry.value.entity.entitySource
    val source: EntitySource = getInternalFileSource(contentRootSource) ?: contentRootSource
    return addSourceFolder(virtualFileManager.fromUrl(url), type, properties, source)
  }

  override fun <P : JpsElement> addSourceFolder(url: String,
                                                type: JpsModuleSourceRootType<P>,
                                                properties: P,
                                                externalSource: ProjectModelExternalSource?): SourceFolder {
    val contentRootSource = currentContentEntry.value.entity.entitySource
    val source = if (externalSource != null) contentRootSource else getInternalFileSource(contentRootSource) ?: contentRootSource
    return addSourceFolder(virtualFileManager.fromUrl(url), type, properties, source)
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
