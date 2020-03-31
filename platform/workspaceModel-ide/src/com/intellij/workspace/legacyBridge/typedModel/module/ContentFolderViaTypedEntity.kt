package com.intellij.workspace.legacyBridge.typedModel.module

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ContentFolder
import com.intellij.openapi.roots.ExcludeFolder
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspace.api.*
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.SOURCE_ROOT_TYPE_ATTRIBUTE
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.URL_ATTRIBUTE

internal abstract class ContentFolderViaTypedEntity(private val entry: ContentEntryViaTypedEntity, private val contentFolderUrl: VirtualFileUrl) : ContentFolder {
  override fun getFile(): VirtualFile? = entry.model.filePointerProvider.getAndCacheFilePointer(contentFolderUrl).file
  override fun getContentEntry(): ContentEntryViaTypedEntity = entry
  override fun getUrl(): String = contentFolderUrl.url
  override fun isSynthetic(): Boolean = false
  override fun equals(other: Any?): Boolean = contentFolderUrl == (other as? ContentFolderViaTypedEntity)?.contentFolderUrl
  override fun hashCode(): Int = contentFolderUrl.hashCode()
}

internal class SourceFolderViaTypedEntity(private val entry: ContentEntryViaTypedEntity, val sourceRootEntity: SourceRootEntity)
  : ContentFolderViaTypedEntity(entry, sourceRootEntity.url), SourceFolder {

  private var packagePrefixVar: String? = null
  private val sourceRootType: JpsModuleSourceRootType<*> by lazy { getSourceRootType(sourceRootEntity.rootType) }

  override fun getRootType() = sourceRootType
  override fun isTestSource() = sourceRootEntity.tests
  override fun getPackagePrefix() = packagePrefixVar ?:
                                    sourceRootEntity.asJavaSourceRoot()?.packagePrefix ?:
                                    sourceRootEntity.asJavaResourceRoot()?.relativeOutputPath?.replace('/', '.') ?: ""

  override fun getJpsElement(): JpsModuleSourceRoot {
    val javaExtensionService = JpsJavaExtensionService.getInstance()

    val rootProperties = when (sourceRootEntity.rootType) {
      JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID, JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID -> {
        val javaSourceRoot = sourceRootEntity.asJavaSourceRoot()
        javaExtensionService.createSourceRootProperties(
          javaSourceRoot?.packagePrefix ?: "", javaSourceRoot?.generated ?: false)
      }

      JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID, JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID -> {
        val javaResourceRoot = sourceRootEntity.asJavaResourceRoot()
        javaExtensionService.createResourceRootProperties(
          javaResourceRoot?.relativeOutputPath ?: "", false)
      }

      else -> loadCustomRootProperties()
    }

    @Suppress("UNCHECKED_CAST")
    return JpsElementFactory.getInstance().createModuleSourceRoot(url, rootType as JpsModuleSourceRootType<JpsElement>, rootProperties)
  }

  private fun loadCustomRootProperties(): JpsElement {
    val elementFactory = JpsElementFactory.getInstance()

    val customSourceRoot = sourceRootEntity.asCustomSourceRoot() ?: return elementFactory.createDummyElement()
    if (customSourceRoot.propertiesXmlTag.isEmpty()) return rootType.createDefaultProperties()

    val serializer = JpsModelSerializerExtension.getExtensions()
      .flatMap { it.moduleSourceRootPropertiesSerializers }
      .firstOrNull { it.type == rootType }
    if (serializer == null) {
      LOG.warn("Module source root type $rootType (${sourceRootEntity.rootType}) is not registered as JpsModelSerializerExtension")
      return elementFactory.createDummyElement()
    }

    return try {
      val element = JDOMUtil.load(customSourceRoot.propertiesXmlTag)
      element.setAttribute(URL_ATTRIBUTE, url)
      element.setAttribute(SOURCE_ROOT_TYPE_ATTRIBUTE, sourceRootEntity.rootType)
      serializer.loadProperties(element)
    }
    catch (t: Throwable) {
      LOG.error("Unable to deserialize source root '${sourceRootEntity.rootType}' from xml '${customSourceRoot.propertiesXmlTag}': ${t.message}", t)
      elementFactory.createDummyElement()
    }
  }

  override fun hashCode() = entry.url.hashCode()
  override fun equals(other: Any?): Boolean {
    if (other !is SourceFolderViaTypedEntity) return false

    if (sourceRootEntity.url != other.sourceRootEntity.url) return false
    if (sourceRootEntity.rootType != other.sourceRootEntity.rootType) return false
    if (sourceRootEntity.tests != other.sourceRootEntity.tests) return false

    val javaSourceRoot = sourceRootEntity.asJavaSourceRoot()
    val otherJavaSourceRoot = other.sourceRootEntity.asJavaSourceRoot()
    if (javaSourceRoot?.generated != otherJavaSourceRoot?.generated) return false
    if (javaSourceRoot?.packagePrefix != otherJavaSourceRoot?.packagePrefix) return false

    val javaResourceRoot = sourceRootEntity.asJavaResourceRoot()
    val otherJavaResourceRoot = other.sourceRootEntity.asJavaResourceRoot()
    if (javaResourceRoot?.generated != otherJavaResourceRoot?.generated) return false
    if (javaResourceRoot?.relativeOutputPath != otherJavaResourceRoot?.relativeOutputPath) return false

    val customRoot = sourceRootEntity.asCustomSourceRoot()
    val otherCustomRoot = other.sourceRootEntity.asCustomSourceRoot()
    if (customRoot?.propertiesXmlTag != otherCustomRoot?.propertiesXmlTag) return false

    return true
  }

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

  private fun getSourceRootType(rootType: String): JpsModuleSourceRootType<*> {
    JpsModelSerializerExtension.getExtensions().forEach { extensions ->
      extensions.moduleSourceRootPropertiesSerializers.forEach {
        if (it.typeId == rootType) return it.type
      }
    }
    return JavaSourceRootType.SOURCE
  }

  companion object {
    val LOG by lazy { logger<ContentFolderViaTypedEntity>() }
  }
}

internal class ExcludeFolderViaTypedEntity(entry: ContentEntryViaTypedEntity, val excludeFolderUrl: VirtualFileUrl)
  : ContentFolderViaTypedEntity(entry, excludeFolderUrl), ExcludeFolder
