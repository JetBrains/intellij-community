package com.intellij.workspace.legacyBridge.typedModel.module

import com.intellij.openapi.roots.ContentFolder
import com.intellij.openapi.roots.ExcludeFolder
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspace.api.*
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension

abstract class ContentFolderViaTypedEntity(private val entry: ContentEntryViaTypedEntity, private val contentFolderUrl: VirtualFileUrl) : ContentFolder {
  private val rootPointer = entry.model.filePointerProvider.getAndCacheFilePointer(contentFolderUrl)

  override fun getFile(): VirtualFile? = rootPointer.file
  override fun getContentEntry(): ContentEntryViaTypedEntity = entry
  override fun getUrl(): String = contentFolderUrl.url
  override fun isSynthetic(): Boolean = false
  override fun equals(other: Any?): Boolean = contentFolderUrl == (other as? ContentFolderViaTypedEntity)?.contentFolderUrl
  override fun hashCode(): Int = contentFolderUrl.hashCode()
}

class SourceFolderViaTypedEntity(private val entry: ContentEntryViaTypedEntity, val sourceRootEntity: SourceRootEntity)
  : ContentFolderViaTypedEntity(entry, sourceRootEntity.url), SourceFolder {

  private var packagePrefixVar: String? = null

  override fun isTestSource() = sourceRootEntity.tests
  override fun getPackagePrefix() = packagePrefixVar ?:
                                    sourceRootEntity.asJavaSourceRoot()?.packagePrefix ?:
                                    sourceRootEntity.asJavaResourceRoot()?.relativeOutputPath?.replace('/', '.') ?: ""
  override fun getRootType() = getSourceRootType(sourceRootEntity.rootType)

  override fun getJpsElement(): JpsModuleSourceRoot {
    val elementFactory = JpsElementFactory.getInstance()
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    val javaSourceRoot = sourceRootEntity.asJavaSourceRoot()
    val rootProperties = if (javaSourceRoot != null) {
      javaExtensionService.createSourceRootProperties(javaSourceRoot.packagePrefix, javaSourceRoot.generated)
    }
    else {
      val javaResourceRoot = sourceRootEntity.asJavaResourceRoot()
      if (javaResourceRoot != null) {
        javaExtensionService.createResourceRootProperties(javaResourceRoot.relativeOutputPath, javaResourceRoot.generated)
      }
      else {
        //todo support properties of other custom root types
        elementFactory.createDummyElement()
      }
    }
    @Suppress("UNCHECKED_CAST")
    return elementFactory.createModuleSourceRoot(url, rootType as JpsModuleSourceRootType<JpsElement>, rootProperties)
  }

  override fun hashCode() = entry.url.hashCode()
  override fun equals(other: Any?) = sourceRootEntity == (other as? SourceFolderViaTypedEntity)?.sourceRootEntity

  override fun setPackagePrefix(packagePrefix: String) {
    if (getPackagePrefix() == packagePrefix) return

    val updater = entry.model.updater ?: error("Model is read-only")

    val javaSourceRoot = sourceRootEntity.asJavaSourceRoot()
    if (javaSourceRoot == null) {
      val javaResourceRoot = sourceRootEntity.asJavaResourceRoot()
      // Original setPackagePrefix silently does nothing on any non-java-source-roots
      if (javaResourceRoot != null) return

      updater { diff ->
        // TODO Replace TempEntitySource with sourceRootEntity.source
        diff.addJavaSourceRootEntity(sourceRootEntity, false, packagePrefix, sourceRootEntity.entitySource)
      }
    } else {
      updater { diff ->
        diff.modifyEntity(ModifiableJavaSourceRootEntity::class.java, javaSourceRoot) {
          this.packagePrefix = packagePrefix
        }
      }
    }

    packagePrefixVar = packagePrefix
  }

  private fun getSourceRootType(rootType: String): JpsModuleSourceRootType<*> =
    JpsModelSerializerExtension.getExtensions()
      .flatMap { it.moduleSourceRootPropertiesSerializers }
      .firstOrNull { it.typeId == rootType }
      ?.type ?: JavaSourceRootType.SOURCE
}

class ExcludeFolderViaTypedEntity(entry: ContentEntryViaTypedEntity, val excludeFolderUrl: VirtualFileUrl)
  : ContentFolderViaTypedEntity(entry, excludeFolderUrl), ExcludeFolder
