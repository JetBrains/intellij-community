// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jdom.Element
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.module.UnknownSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer
import org.jetbrains.jps.model.serialization.module.UnknownSourceRootPropertiesSerializer

object SourceRootPropertiesHelper {
  @Suppress("UNCHECKED_CAST")
  fun <P : JpsElement?> findSerializer(rootType: JpsModuleSourceRootType<P>): JpsModuleSourceRootPropertiesSerializer<P>? {
    val serializer = if (rootType is UnknownSourceRootType) {
      UnknownSourceRootPropertiesSerializer.forType(rootType as UnknownSourceRootType)
    }
    else {
      JpsModelSerializerExtension.getExtensions()
        .flatMap { it.moduleSourceRootPropertiesSerializers }
        .firstOrNull { it.type == rootType }
    }
    return serializer as JpsModuleSourceRootPropertiesSerializer<P>?
  }

  internal fun hasEqualProperties(entity: SourceRootEntity, sourceRoot: JpsModuleSourceRoot): Boolean {
    val properties = sourceRoot.properties
    val javaSourceEntity = entity.asJavaSourceRoot()
    if (javaSourceEntity != null) {
      return properties is JavaSourceRootProperties && javaSourceEntity.generated == properties.isForGeneratedSources
             && javaSourceEntity.packagePrefix == properties.packagePrefix
    }
    val javaResourceEntity = entity.asJavaResourceRoot()
    if (javaResourceEntity != null) {
      return properties is JavaResourceRootProperties && javaResourceEntity.generated == properties.isForGeneratedSources
             && javaResourceEntity.relativeOutputPath == properties.relativeOutputPath
    }

    val customEntity = entity.asCustomSourceRoot()
    if (customEntity == null) {
      return properties is JpsDummyElement
    }
    val serializer = findSerializer(sourceRoot.rootType as JpsModuleSourceRootType<JpsElement>)
                     ?: return false
    val propertiesXml = savePropertiesToString(serializer, properties)
    return propertiesXml == customEntity.propertiesXmlTag
  }

  private fun <P : JpsElement?> savePropertiesToString(serializer: JpsModuleSourceRootPropertiesSerializer<P>, properties: P): String {
    val sourceElement = Element(JpsModuleRootModelSerializer.SOURCE_FOLDER_TAG)
    serializer.saveProperties(properties, sourceElement)
    return JDOMUtil.writeElement(sourceElement)
  }

  internal fun applyChanges(diff: WorkspaceEntityStorageBuilder, entity: SourceRootEntity, actualSourceRootData: JpsModuleSourceRoot) {
    if (hasEqualProperties(entity, actualSourceRootData)) {
      return
    }

    val properties = actualSourceRootData.properties
    val javaSourceEntity = entity.asJavaSourceRoot()
    val javaResourceEntity = entity.asJavaResourceRoot()
    if (javaSourceEntity != null && properties is JavaSourceRootProperties) {
      diff.modifyEntity(ModifiableJavaSourceRootEntity::class.java, javaSourceEntity) {
        generated = properties.isForGeneratedSources
        packagePrefix = properties.packagePrefix
      }
    }
    else if (javaResourceEntity != null && properties is JavaResourceRootProperties) {
      diff.modifyEntity(ModifiableJavaResourceRootEntity::class.java, javaResourceEntity) {
        generated = properties.isForGeneratedSources
        relativeOutputPath = properties.relativeOutputPath
      }
    }
    else {
      val customEntity = entity.asCustomSourceRoot() ?: return
      val serializer = findSerializer(actualSourceRootData.rootType as JpsModuleSourceRootType<JpsElement>)
                       ?: return
      val propertiesXml = savePropertiesToString(serializer, properties)
      diff.modifyEntity(ModifiableCustomSourceRootPropertiesEntity::class.java, customEntity) {
        propertiesXmlTag = propertiesXml
      }
    }

  }

  internal fun <P : JpsElement> addPropertiesEntity(diff: WorkspaceEntityStorageDiffBuilder,
                                                    sourceRootEntity: SourceRootEntity,
                                                    properties: P,
                                                    serializer: JpsModuleSourceRootPropertiesSerializer<P>) {
    when (serializer.typeId) {
      JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID, JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID -> diff.addJavaSourceRootEntity(
        sourceRoot = sourceRootEntity,
        generated = (properties as JavaSourceRootProperties).isForGeneratedSources,
        packagePrefix = properties.packagePrefix
      )

      JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID, JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID -> diff.addJavaResourceRootEntity(
        sourceRoot = sourceRootEntity,
        generated = (properties as JavaResourceRootProperties).isForGeneratedSources,
        relativeOutputPath = properties.relativeOutputPath
      )

      else -> if (properties !is JpsDummyElement) {
        diff.addCustomSourceRootPropertiesEntity(
          sourceRoot = sourceRootEntity,
          propertiesXmlTag = savePropertiesToString(serializer, properties)
        )
      }
    }
  }

  internal fun loadRootProperties(entity: SourceRootEntity,
                                  rootType: JpsModuleSourceRootType<out JpsElement>,
                                  url: String): JpsModuleSourceRoot {
    val javaExtensionService = JpsJavaExtensionService.getInstance()

    val rootProperties = when (entity.rootType) {
      JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID, JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID -> {
        val javaSourceRoot = entity.asJavaSourceRoot()
        javaExtensionService.createSourceRootProperties(
          javaSourceRoot?.packagePrefix ?: "", javaSourceRoot?.generated ?: false)
      }

      JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID, JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID -> {
        val javaResourceRoot = entity.asJavaResourceRoot()
        javaExtensionService.createResourceRootProperties(
          javaResourceRoot?.relativeOutputPath ?: "", false)
      }

      else -> loadCustomRootProperties(entity, rootType)
    }

    return (@Suppress("UNCHECKED_CAST")
    JpsElementFactory.getInstance().createModuleSourceRoot(url, rootType as JpsModuleSourceRootType<JpsElement>, rootProperties))
  }

  internal fun loadCustomRootProperties(entity: SourceRootEntity, rootType: JpsModuleSourceRootType<out JpsElement>): JpsElement {
    val elementFactory = JpsElementFactory.getInstance()

    val customSourceRoot = entity.asCustomSourceRoot()
    if (customSourceRoot == null || customSourceRoot.propertiesXmlTag.isEmpty()) return rootType.createDefaultProperties()

    val serializer = findSerializer(rootType)
    if (serializer == null) {
      LOG.warn("Module source root type $rootType (${entity.rootType}) is not registered as JpsModelSerializerExtension")
      return elementFactory.createDummyElement()
    }

    return try {
      val element = JDOMUtil.load(customSourceRoot.propertiesXmlTag)
      element.setAttribute(JpsModuleRootModelSerializer.SOURCE_ROOT_TYPE_ATTRIBUTE, entity.rootType)
      serializer.loadProperties(element)
    }
    catch (t: Throwable) {
      LOG.error("Unable to deserialize source root '${entity.rootType}' from xml '${customSourceRoot.propertiesXmlTag}': ${t.message}", t)
      elementFactory.createDummyElement()
    }
  }

  private val LOG = logger<SourceRootPropertiesHelper>()
}