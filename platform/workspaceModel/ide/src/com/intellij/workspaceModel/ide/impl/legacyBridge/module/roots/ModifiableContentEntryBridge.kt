package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ExcludeFolder
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.CachedValueImpl
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.storage.bridgeEntities.ModifiableContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addSourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.asJavaResourceRoot
import com.intellij.workspaceModel.storage.bridgeEntities.asJavaSourceRoot
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer

internal class ModifiableContentEntryBridge(
  private val diff: WorkspaceEntityStorageDiffBuilder,
  private val modifiableRootModel: ModifiableRootModelBridge,
  val contentEntryUrl: VirtualFileUrl
): ContentEntry {
  private val LOG = Logger.getInstance(javaClass)
  private val virtualFileManager = VirtualFileUrlManager.getInstance(modifiableRootModel.project)

  private val currentContentEntry = CachedValueImpl<ContentEntryBridge> {
    val contentEntry = modifiableRootModel.currentModel.contentEntries.firstOrNull { it.url == contentEntryUrl.url } as? ContentEntryBridge
      ?: error("Unable to find content entry in parent modifiable root model by url: $contentEntryUrl")
    CachedValueProvider.Result.createSingleDependency<ContentEntryBridge>(contentEntry, modifiableRootModel)
  }

  private fun <P : JpsElement?> addSourceFolder(sourceFolderUrl: VirtualFileUrl, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder {
    if (!contentEntryUrl.isEqualOrParentOf(sourceFolderUrl)) {
      error("Source folder $sourceFolderUrl must be under content entry $contentEntryUrl")
    }

    val duplicate = findDuplicate(sourceFolderUrl, type, properties)
    if (duplicate != null) {
      LOG.debug("Source folder for '$sourceFolderUrl' and type '$type' already exist")
      return duplicate
    }

    @Suppress("UNCHECKED_CAST")
    val serializer: JpsModuleSourceRootPropertiesSerializer<P> = SourceRootPropertiesHelper.findSerializer(type)
                                                                 ?: error("Module source root type $type is not registered as JpsModelSerializerExtension")

    val contentRootEntity = currentContentEntry.value.entity
    val entitySource = contentRootEntity.entitySource
    val sourceRootEntity = diff.addSourceRootEntity(
      contentRoot = contentRootEntity,
      url = sourceFolderUrl,
      tests = type.isForTests,
      rootType = serializer.typeId,
      source = entitySource
    )

    SourceRootPropertiesHelper.addPropertiesEntity(diff, sourceRootEntity, entitySource, properties, serializer)

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
      Logger.getInstance(javaClass).error("SourceFolder ${sourceFolder.url} is not present under content entry $contentEntryUrl")
      return
    }

    diff.removeEntity(sourceRootEntity)
  }

  override fun clearSourceFolders() {
    currentContentEntry.value.sourceRootEntities.forEach { sourceRoot -> diff.removeEntity(sourceRoot) }
  }

  private fun addExcludeFolder(excludeUrl: VirtualFileUrl): ExcludeFolder {
    if (!contentEntryUrl.isEqualOrParentOf(excludeUrl)) {
      error("Exclude folder $excludeUrl must be under content entry $contentEntryUrl")
    }

    updateContentEntry {
      excludedUrls = if (excludedUrls.contains(excludeUrl)) excludedUrls else (excludedUrls + excludeUrl)
    }

    return currentContentEntry.value.excludeFolders.firstOrNull {
      it.url == excludeUrl.url
    } ?: error("Exclude folder $excludeUrl must be present after adding it to content entry $contentEntryUrl")
  }

  override fun addExcludeFolder(file: VirtualFile): ExcludeFolder = addExcludeFolder(file.toVirtualFileUrl(virtualFileManager))
  override fun addExcludeFolder(url: String): ExcludeFolder = addExcludeFolder(virtualFileManager.fromUrl(url))

  override fun removeExcludeFolder(excludeFolder: ExcludeFolder) {
    val virtualFileUrl = (excludeFolder as ExcludeFolderBridge).excludeFolderUrl

    updateContentEntry {
      if (!excludedUrls.contains(virtualFileUrl)) {
        error("Exclude folder ${excludeFolder.url} is not under content entry $contentEntryUrl")
      }

      excludedUrls = excludedUrls.filter { url -> url != virtualFileUrl }
    }
  }

  private fun updateContentEntry(updater: ModifiableContentRootEntity.() -> Unit) {
    diff.modifyEntity(ModifiableContentRootEntity::class.java, currentContentEntry.value.entity, updater)
  }

  override fun removeExcludeFolder(url: String): Boolean {
    val virtualFileUrl = virtualFileManager.fromUrl(url)

    val excludedUrls = currentContentEntry.value.entity.excludedUrls
    if (!excludedUrls.contains(virtualFileUrl)) return false

    updateContentEntry {
      this.excludedUrls = excludedUrls.filter { excludedUrl -> excludedUrl != virtualFileUrl }
    }

    return true
  }

  override fun clearExcludeFolders() {
    updateContentEntry {
      excludedUrls = emptyList()
    }
  }

  override fun addExcludePattern(pattern: String) {
    updateContentEntry {
      excludedPatterns = if (excludedPatterns.contains(pattern)) excludedPatterns else (excludedPatterns + pattern)
    }
  }

  override fun removeExcludePattern(pattern: String) {
    updateContentEntry {
      excludedPatterns = emptyList()
    }
  }

  override fun setExcludePatterns(patterns: MutableList<String>) {
    updateContentEntry {
      excludedPatterns = patterns.toList()
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
    addSourceFolder(file, if (isTestSource) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE)

  override fun <P : JpsElement?> addSourceFolder(file: VirtualFile, type: JpsModuleSourceRootType<P>): SourceFolder =
    addSourceFolder(file, type, type.createDefaultProperties())

  override fun addSourceFolder(url: String, isTestSource: Boolean): SourceFolder =
    addSourceFolder(url, if (isTestSource) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE)

  override fun <P : JpsElement?> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>): SourceFolder =
    addSourceFolder(url, type, type.createDefaultProperties())

  override fun <P : JpsElement?> addSourceFolder(file: VirtualFile, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder =
    addSourceFolder(file.toVirtualFileUrl(virtualFileManager), type, properties)

  override fun <P : JpsElement?> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder =
    addSourceFolder(virtualFileManager.fromUrl(url), type, properties)

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
