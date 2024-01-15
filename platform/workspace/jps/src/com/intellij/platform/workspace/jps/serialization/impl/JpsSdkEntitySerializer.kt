// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkRoot
import com.intellij.platform.workspace.jps.entities.SdkRootTypeId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.ConcurrentFactoryMap
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer

@NonNls
private const val SDK_TABLE_COMPONENT_NAME = "ProjectJdkTable"

@NonNls
private const val ELEMENT_JDK = "jdk"

@NonNls
private const val ELEMENT_NAME = "name"

@NonNls
private const val ATTRIBUTE_VALUE = "value"

@NonNls
private const val ELEMENT_TYPE = "type"

@NonNls
private val ELEMENT_VERSION = "version"

@NonNls
private val ELEMENT_ROOTS = "roots"

@NonNls
private val ELEMENT_ROOT = "root"

@NonNls
private val ELEMENT_HOMEPATH = "homePath"

@NonNls
const val ELEMENT_ADDITIONAL = "additional"


class JpsSdkEntitySerializer(val entitySource: JpsGlobalFileEntitySource, private val sortedRootTypes: List<String>): JpsFileEntityTypeSerializer<SdkEntity> {
  private val LOG = logger<JpsSdkEntitySerializer>()
  private val rootTypes = ConcurrentFactoryMap.createMap<String, SdkRootTypeId> { SdkRootTypeId(it) }
  override val isExternalStorage: Boolean
    get() = false
  override val internalEntitySource: JpsFileEntitySource
    get() = entitySource
  override val fileUrl: VirtualFileUrl
    get() = entitySource.file
  override val mainEntityClass: Class<SdkEntity>
    get() = SdkEntity::class.java


  override fun loadEntities(reader: JpsFileContentReader, errorReporter: ErrorReporter,
                            virtualFileManager: VirtualFileUrlManager): LoadingResult<Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>> {
    val sdkTag = reader.loadComponent(entitySource.file.url, SDK_TABLE_COMPONENT_NAME) ?: return LoadingResult(emptyMap(), null)
    val sdkEntities = sdkTag.getChildren(ELEMENT_JDK).map { sdkElement -> loadSdkEntity(sdkElement, virtualFileManager) }
    return LoadingResult(mapOf(SdkEntity::class.java to sdkEntities))
  }

  fun loadSdkEntity(sdkElement: Element, virtualFileManager: VirtualFileUrlManager ): SdkEntity {
    val sdkName = sdkElement.getChild(ELEMENT_NAME).getAttributeValue(ATTRIBUTE_VALUE)
    val sdkType = sdkElement.getChild(ELEMENT_TYPE).getAttributeValue(ATTRIBUTE_VALUE)

    val versionValue = sdkElement.getAttributeValue(ELEMENT_VERSION)
    if ("2" != versionValue) {
      throw InvalidDataException("Too old version is not supported: $versionValue")
    }
    val sdkVersion = sdkElement.getChild(ELEMENT_VERSION)?.getAttributeValue(ATTRIBUTE_VALUE)
    val homePath = sdkElement.getChild(ELEMENT_HOMEPATH).getAttributeValueStrict(ATTRIBUTE_VALUE)
    val homePathVfu = virtualFileManager.fromUrl(homePath)

    val roots = readRoots(sdkElement.getChildTagStrict(ELEMENT_ROOTS), virtualFileManager)

    val additionalDataElement = sdkElement.getChild(ELEMENT_ADDITIONAL)
    val additionalData = if (additionalDataElement != null) JDOMUtil.write(additionalDataElement) else ""
    return SdkEntity(sdkName, sdkType, roots, additionalData, entitySource) {
      this.homePath = homePathVfu
      this.version = sdkVersion
    }
  }

  /**
   * <roots>
   *    <sourcePath>
   *        <root type="composite">
   *          <root url="jar://I:/Java/jdk1.8/src.zip!/" type="simple" />
   *          <root url="jar://I:/Java/jdk1.8/javafx-src.zip!/" type="simple" />
   *        </root>
   *    </sourcePath>
   * </roots>
   */
  private fun readRoots(rootsElement: Element, virtualFileManager: VirtualFileUrlManager): List<SdkRoot> {
    val result = mutableListOf<SdkRoot>()
    for (rootElement in rootsElement.children) {
      val rootType = rootElement.name
      val composites = rootElement.children
      if (composites.size != 1) {
        LOG.error("Single child expected by $composites found")
      }
      val composite = composites[0]
      for (rootTag in composite.getChildren(JpsJavaModelSerializerExtension.ROOT_TAG)) {
        val url = rootTag.getAttributeValueStrict(JpsModuleRootModelSerializer.URL_ATTRIBUTE)
        result.add(SdkRoot(virtualFileManager.fromUrl(url), rootTypes[rootType]!!))
      }
    }
    return result
  }

  override fun checkAndAddToBuilder(builder: MutableEntityStorage, orphanage: MutableEntityStorage,
                                    newEntities: Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>) {
    newEntities.values.flatten().forEach { builder addEntity  it }
  }

  override fun saveEntities(mainEntities: Collection<SdkEntity>, entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            storage: EntityStorage, writer: JpsFileContentWriter) {
    val componentTag = JDomSerializationUtil.createComponentElement(SDK_TABLE_COMPONENT_NAME)
    mainEntities.forEach { sdkEntity ->
      val sdkElement = Element(ELEMENT_JDK)
      saveSdkEntity(sdkElement, sdkEntity)
      componentTag.addContent(sdkElement)
    }
    writer.saveComponent(fileUrl.url, SDK_TABLE_COMPONENT_NAME, componentTag)
  }

  fun saveSdkEntity(sdkRootElement: Element, sdkEntity: SdkEntity) {
    sdkRootElement.setAttribute(ELEMENT_VERSION, "2")

    val name = Element(ELEMENT_NAME)
    name.setAttribute(ATTRIBUTE_VALUE, sdkEntity.name)
    sdkRootElement.addContent(name)

    sdkEntity.type?.let {
      val sdkType = Element(ELEMENT_TYPE)
      sdkType.setAttribute(ATTRIBUTE_VALUE, it)
      sdkRootElement.addContent(sdkType)
    }

    sdkEntity.version?.let {
      val version = Element(ELEMENT_VERSION)
      version.setAttribute(ATTRIBUTE_VALUE, it)
      sdkRootElement.addContent(version)
    }

    val home = Element(ELEMENT_HOMEPATH)
    home.setAttribute(ATTRIBUTE_VALUE, sdkEntity.homePath?.url)
    sdkRootElement.addContent(home)

    val sortedRoots = sdkEntity.roots.groupBy { it.type.name }.toSortedMap()
    val rootsElement = Element(ELEMENT_ROOTS)
    sortedRootTypes.forEach { rootType ->
      val sdkRoots = sortedRoots[rootType] ?: emptyList()
      rootsElement.addContent(writeRoots(rootType, sdkRoots))
    }
    sdkRootElement.addContent(rootsElement)

    val additionalData = sdkEntity.additionalData
    if (additionalData.isNotBlank()) {
      sdkRootElement.addContent(JDOMUtil.load(additionalData))
    }
  }

  private fun writeRoots(rootType: String, roots: List<SdkRoot>): Element {
    val composite = Element(ELEMENT_ROOT)
    composite.setAttribute("type", "composite")
    roots.forEach { root ->
      val rootElement = Element(ELEMENT_ROOT)
      rootElement.setAttribute("url", root.url.url)
      rootElement.setAttribute("type", "simple")
      composite.addContent(rootElement)
    }
    val element = Element(rootType)
    element.addContent(composite)
    return element
  }

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, SDK_TABLE_COMPONENT_NAME, null)
  }
}