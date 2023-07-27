// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.SdkMainEntity
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


val SDK_TABLE_COMPONENT_NAME = "ProjectJdkTable"

val ELEMENT_JDK = "jdk"

@NonNls
val ELEMENT_NAME = "name"

@NonNls
val ATTRIBUTE_VALUE = "value"

@NonNls
val ELEMENT_TYPE = "type"

@NonNls
private val ELEMENT_VERSION = "version"

@NonNls
private val ELEMENT_ROOTS = "roots"

@NonNls
private val ELEMENT_HOMEPATH = "homePath"

@NonNls
val ELEMENT_ADDITIONAL = "additional"


class JpsSdkEntitySerializer(val entitySource: JpsGlobalFileEntitySource): JpsFileEntitiesSerializer<SdkMainEntity> {
  private val LOG = logger<JpsSdkEntitySerializer>()
  private val rootTypes = ConcurrentFactoryMap.createMap<String, SdkRootTypeId> { SdkRootTypeId(it) }

  //private val myRoots: ConcurrentHashMap<OrderRootType, VirtualFilePointerContainer?> = ConcurrentHashMap()

  override val internalEntitySource: JpsFileEntitySource
    get() = entitySource
  override val fileUrl: VirtualFileUrl
    get() = entitySource.file
  override val mainEntityClass: Class<SdkMainEntity>
    get() = SdkMainEntity::class.java


  override fun loadEntities(reader: JpsFileContentReader, errorReporter: ErrorReporter,
                            virtualFileManager: VirtualFileUrlManager): LoadingResult<Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>> {
    val sdkTag = reader.loadComponent(entitySource.file.url, SDK_TABLE_COMPONENT_NAME) ?: return LoadingResult(emptyMap(), null)
    val sdkEntities = sdkTag.getChildren(ELEMENT_JDK).map { sdkElement ->
      val sdkName = sdkElement.getChild(ELEMENT_NAME).getAttributeValue(ATTRIBUTE_VALUE)
      val sdkType = sdkElement.getChild(ELEMENT_TYPE).getAttributeValue(ATTRIBUTE_VALUE)
      //val mySdkType = ProjectJdkTable.getInstance().getSdkTypeByName(sdkTypeName ?: "")

      val versionValue = sdkElement.getAttributeValue(ELEMENT_VERSION)
      if ("2" != versionValue) {
        throw InvalidDataException("Too old version is not supported: $versionValue")
      }
      val sdkVersion = sdkElement.getChild(ELEMENT_VERSION).getAttributeValue(ATTRIBUTE_VALUE)
      val homePath = sdkElement.getChild(ELEMENT_HOMEPATH).getAttributeValueStrict(ATTRIBUTE_VALUE)
      val homePathVfu = virtualFileManager.fromUrl(homePath)

      val roots = readRoots(sdkElement.getChildTagStrict(ELEMENT_ROOTS), virtualFileManager)

      // TODO the same problem as with FacetConfiguration we have 7 types of additional data for SDK so 7 entities
      val additionalDataElement = sdkElement.getChild(ELEMENT_ADDITIONAL)
      SdkMainEntity(sdkName, sdkType, homePathVfu, roots, JDOMUtil.write(additionalDataElement), entitySource) {
        this.version = sdkVersion
      }
    }

    return LoadingResult(mapOf(SdkMainEntity::class.java to sdkEntities))
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

  override fun saveEntities(mainEntities: Collection<SdkMainEntity>, entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                            storage: EntityStorage, writer: JpsFileContentWriter) {
    val componentTag = JDomSerializationUtil.createComponentElement(SDK_TABLE_COMPONENT_NAME)
    mainEntities.forEach { sdkEntity ->
      val sdkElement = Element(ELEMENT_JDK)
      saveSdkEntity(sdkElement, sdkEntity)
      componentTag.addContent(sdkElement)
    }
    writer.saveComponent(fileUrl.url, SDK_TABLE_COMPONENT_NAME, componentTag)
  }

  private fun saveSdkEntity(sdkRootElement: Element, sdkEntity: SdkMainEntity) {
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
    home.setAttribute(ATTRIBUTE_VALUE, sdkEntity.homePath.url)
    sdkRootElement.addContent(home)

    val rootsElement = Element(ELEMENT_ROOTS)
    val sortedRoots = sdkEntity.roots.groupBy { it.type.name }.toSortedMap()
    sortedRoots.forEach { (rootType, roots) ->
      rootsElement.addContent(writeRoots(rootType, roots))
    }
    sdkRootElement.addContent(rootsElement)

    val additional = Element(ELEMENT_ADDITIONAL)
    additional.addContent(JDOMUtil.load(sdkEntity.additionalData))
    sdkRootElement.addContent(additional)
  }

  private fun writeRoots(rootType: String, roots: List<SdkRoot>): Element {
    val composite = Element("root")
    composite.setAttribute("type", "composite")
    roots.forEach { root ->
      val rootElement = Element("root")
      rootElement.setAttribute("url", root.url.url)
      rootElement.setAttribute("type", "simple")
      composite.addContent(rootElement)
    }
    val element = Element(rootType)
    element.addContent(composite)
    return element
  }


  //
  //private fun setNoCopyJars(url: String) {
  //  if (StandardFileSystems.JAR_PROTOCOL == VirtualFileManager.extractProtocol(url)) {
  //    val path = VirtualFileManager.extractPath(url)
  //    val fileSystem = StandardFileSystems.jar()
  //    if (fileSystem is JarCopyingFileSystem) {
  //      (fileSystem as JarCopyingFileSystem).setNoCopyJarForPath(path)
  //    }
  //  }
  //}

  //private val tellAllProjectsTheirRootsAreGoingToChange: VirtualFilePointerListener = object : VirtualFilePointerListener {
  //  override fun beforeValidityChanged(pointers: Array<VirtualFilePointer>) {
  //    //todo check if this sdk is really used in the project
  //    for (project in ProjectManager.getInstance().openProjects) {
  //      val listener = (ProjectRootManager.getInstance(project) as ProjectRootManagerImpl).rootsValidityChangedListener
  //      listener.beforeValidityChanged(pointers)
  //    }
  //  }
  //
  //  override fun validityChanged(pointers: Array<VirtualFilePointer>) {
  //    //todo check if this sdk is really used in the project
  //    for (project in ProjectManager.getInstance().openProjects) {
  //      val listener = (ProjectRootManager.getInstance(project) as ProjectRootManagerImpl).rootsValidityChangedListener
  //      listener.validityChanged(pointers)
  //    }
  //  }
  //}
}