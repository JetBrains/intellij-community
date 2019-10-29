package com.intellij.workspace.legacyBridge.roots

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ExcludeFolder
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.workspace.api.*
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModifiableRootModel
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeModifiableBase
import com.intellij.workspace.legacyBridge.typedModel.module.ContentEntryViaTypedEntity
import com.intellij.workspace.legacyBridge.typedModel.module.ExcludeFolderViaTypedEntity
import com.intellij.workspace.legacyBridge.typedModel.module.SourceFolderViaTypedEntity
import com.intellij.util.CachedValueImpl
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension

class LegacyBridgeModifiableContentEntryImpl(
  private val diff: TypedEntityStorageDiffBuilder,
  private val modifiableRootModel: LegacyBridgeModifiableRootModel,
  val contentEntryUrl: VirtualFileUrl
): ContentEntry {

  private val currentContentEntry = CachedValueImpl<ContentEntryViaTypedEntity> {
    val contentEntry = modifiableRootModel.currentModel.contentEntries.firstOrNull { it.url == contentEntryUrl.url } as? ContentEntryViaTypedEntity
      ?: error("Unable to find content entry in parent modifiable root model by url: $contentEntryUrl")
    CachedValueProvider.Result.createSingleDependency<ContentEntryViaTypedEntity>(contentEntry, diff)
  }

  private fun <P : JpsElement?> addSourceFolder(sourceFolderUrl: VirtualFileUrl, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder {
    if (!contentEntryUrl.isEqualOrParentOf(sourceFolderUrl)) {
      error("Source folder $sourceFolderUrl must be under content entry $contentEntryUrl")
    }

    val typeId = JpsModelSerializerExtension.getExtensions()
                   .flatMap { it.moduleSourceRootPropertiesSerializers }
                   .firstOrNull { it.type == type }
                   ?.typeId ?: error("Module source root type $type is not registered as JpsModelSerializerExtension")

    val entitySource = currentContentEntry.value.entity.entitySource
    val sourceRootEntity = diff.addSourceRootEntity(
      module = currentContentEntry.value.entity.module,
      url = sourceFolderUrl,
      tests = type.isForTests,
      rootType = typeId,
      source = entitySource
    )

    when (properties) {
      is JavaSourceRootProperties -> diff.addJavaSourceRootEntity(
        sourceRoot = sourceRootEntity,
        generated = properties.isForGeneratedSources,
        packagePrefix = properties.packagePrefix,
        source = entitySource
      )

      is JavaResourceRootProperties -> diff.addJavaResourceRootEntity(
        sourceRoot = sourceRootEntity,
        generated = properties.isForGeneratedSources,
        relativeOutputPath = properties.relativeOutputPath,
        source = entitySource
      )

      is JpsDummyElement -> Unit

      null -> Unit

      else -> error("Unsupported source root properties: $properties")
    }

    return currentContentEntry.value.sourceFolders.firstOrNull {
      it.url == sourceFolderUrl.url && it.rootType == type
    } ?: error("Source folder for '$sourceFolderUrl' and type '$type' was not found after adding")
  }

  override fun removeSourceFolder(sourceFolder: SourceFolder) {
    val legacyBridgeSourceFolder = sourceFolder as SourceFolderViaTypedEntity
    val sourceRootEntity = currentContentEntry.value.sourceRootEntities.firstOrNull { it == legacyBridgeSourceFolder.sourceRootEntity }
    if (sourceRootEntity == null) {
      Logger.getInstance(javaClass).error("SourceFolder ${sourceFolder.url} is not present under content entry $contentEntryUrl")
      return
    }

    diff.removeEntity(sourceRootEntity)
  }

  override fun clearSourceFolders() {
    if (currentContentEntry.value.sourceFolders.isEmpty()) return

    currentContentEntry.value.sourceRootEntities.forEach {
      diff.removeEntity(it)
    }

    if (LegacyBridgeModifiableBase.assertChangesApplied && currentContentEntry.value.sourceFolders.isNotEmpty()) {
      error("Source folders are still present after clearSourceFolders()")
    }
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

  override fun addExcludeFolder(file: VirtualFile): ExcludeFolder = addExcludeFolder(file.virtualFileUrl)
  override fun addExcludeFolder(url: String): ExcludeFolder = addExcludeFolder(VirtualFileUrlManager.fromUrl(url))

  override fun removeExcludeFolder(excludeFolder: ExcludeFolder) {
    val virtualFileUrl = (excludeFolder as ExcludeFolderViaTypedEntity).excludeFolderUrl

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
    val virtualFileUrl = VirtualFileUrlManager.fromUrl(url)

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

    if (LegacyBridgeModifiableBase.assertChangesApplied && currentContentEntry.value.excludeFolderUrls.isNotEmpty()) {
      error("excludeFolderUrls is not empty after clearExcludeFolders()")
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
    addSourceFolder(file.virtualFileUrl, type, properties)

  override fun <P : JpsElement?> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder =
    addSourceFolder(VirtualFileUrlManager.fromUrl(url), type, properties)

  override fun getFile(): VirtualFile? = currentContentEntry.value.file
  override fun getUrl(): String = currentContentEntry.value.url
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
