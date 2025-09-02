// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.java.workspace.entities.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.entities.CustomSourceRootPropertiesEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.customSourceRootProperties
import com.intellij.platform.workspace.jps.entities.modifyCustomSourceRootPropertiesEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
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

@ApiStatus.Internal
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

  @JvmStatic
  fun <P : JpsElement> createPropertiesCopy(properties: P, type: JpsModuleSourceRootType<P>): P {
    val serializer = findSerializer(type)
    if (serializer == null) {
      LOG.warn("Cannot find serializer for $type")
      return type.createDefaultProperties()
    }
    val element = Element("properties")
    serializer.saveProperties(properties, element)
    return serializer.loadProperties(element)
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

    val customEntity = entity.customSourceRootProperties
    if (customEntity == null) {
      return properties is JpsDummyElement
    }
    val serializer = findSerializer(sourceRoot.rootType as JpsModuleSourceRootType<JpsElement>)
                     ?: return false
    val propertiesXml = savePropertiesToString(serializer, properties)
    return propertiesXml == customEntity.propertiesXmlTag
  }

  private fun <P : JpsElement> savePropertiesToString(serializer: JpsModuleSourceRootPropertiesSerializer<P>, properties: P): String {
    val sourceElement = Element(JpsModuleRootModelSerializer.SOURCE_FOLDER_TAG)
    serializer.saveProperties(properties, sourceElement)
    return JDOMUtil.writeElement(sourceElement)
  }

  internal fun applyChanges(diff: MutableEntityStorage, entity: SourceRootEntity, actualSourceRootData: JpsModuleSourceRoot) {
    if (hasEqualProperties(entity, actualSourceRootData)) {
      return
    }

    val properties = actualSourceRootData.properties
    val javaSourceEntity = entity.asJavaSourceRoot()
    val javaResourceEntity = entity.asJavaResourceRoot()
    if (javaSourceEntity != null && properties is JavaSourceRootProperties) {
      diff.modifyJavaSourceRootPropertiesEntity(javaSourceEntity) {
        generated = properties.isForGeneratedSources
        packagePrefix = properties.packagePrefix
      }
    }
    else if (javaResourceEntity != null && properties is JavaResourceRootProperties) {
      diff.modifyJavaResourceRootPropertiesEntity(javaResourceEntity) {
        generated = properties.isForGeneratedSources
        relativeOutputPath = properties.relativeOutputPath
      }
    }
    else {
      val customEntity = entity.customSourceRootProperties ?: return
      val serializer = findSerializer(actualSourceRootData.rootType as JpsModuleSourceRootType<JpsElement>)
                       ?: return
      val propertiesXml = savePropertiesToString(serializer, properties)
      diff.modifyCustomSourceRootPropertiesEntity(customEntity) {
        propertiesXmlTag = propertiesXml
      }
    }

  }

  internal fun <P : JpsElement> addPropertiesEntity(sourceRootEntity: SourceRootEntity.Builder,
                                                    properties: P,
                                                    serializer: JpsModuleSourceRootPropertiesSerializer<P>) {
    when (serializer.typeId) {
      JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID, JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID -> {
        sourceRootEntity.javaSourceRoots += JavaSourceRootPropertiesEntity(
          generated = (properties as JavaSourceRootProperties).isForGeneratedSources,
          packagePrefix = properties.packagePrefix,
          entitySource = sourceRootEntity.entitySource)
      }

      JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID, JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID -> {
        sourceRootEntity.javaResourceRoots += JavaResourceRootPropertiesEntity(
          generated = (properties as JavaResourceRootProperties).isForGeneratedSources,
          relativeOutputPath = properties.relativeOutputPath,
          entitySource = sourceRootEntity.entitySource)
      }

      else -> if (properties !is JpsDummyElement) {
        sourceRootEntity.customSourceRootProperties = CustomSourceRootPropertiesEntity(
          propertiesXmlTag = savePropertiesToString(serializer, properties),
          entitySource = sourceRootEntity.entitySource
        )
      }
    }
  }

  internal fun loadRootProperties(entity: SourceRootEntity,
                                  rootType: JpsModuleSourceRootType<out JpsElement>,
                                  url: String): JpsModuleSourceRoot {
    val javaExtensionService = JpsJavaExtensionService.getInstance()

    val rootProperties = when (entity.rootTypeId.name) {
      JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID, JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID -> {
        val javaSourceRoot = entity.asJavaSourceRoot()
        javaExtensionService.createSourceRootProperties(
          javaSourceRoot?.packagePrefix ?: "", javaSourceRoot?.generated ?: false)
      }

      JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID, JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID -> {
        val javaResourceRoot = entity.asJavaResourceRoot()
        javaExtensionService.createResourceRootProperties(
          javaResourceRoot?.relativeOutputPath ?: "", javaResourceRoot?.generated ?: false)
      }

      else -> loadCustomRootProperties(entity, rootType)
    }

    return (@Suppress("UNCHECKED_CAST")
    JpsElementFactory.getInstance().createModuleSourceRoot(url, rootType as JpsModuleSourceRootType<JpsElement>, rootProperties))
  }

  private fun loadCustomRootProperties(entity: SourceRootEntity, rootType: JpsModuleSourceRootType<out JpsElement>): JpsElement {
    val elementFactory = JpsElementFactory.getInstance()

    val customSourceRoot = entity.customSourceRootProperties
    if (customSourceRoot == null || customSourceRoot.propertiesXmlTag.isEmpty()) return rootType.createDefaultProperties()

    val serializer = findSerializer(rootType)
    if (serializer == null) {
      LOG.warn("Module source root type $rootType (${entity.rootTypeId}) is not registered as JpsModelSerializerExtension")
      return elementFactory.createDummyElement()
    }

    return try {
      val element = JDOMUtil.load(customSourceRoot.propertiesXmlTag)
      element.setAttribute(JpsModuleRootModelSerializer.SOURCE_ROOT_TYPE_ATTRIBUTE, entity.rootTypeId.name)
      serializer.loadProperties(element)
    }
    catch (t: Throwable) {
      LOG.error("Unable to deserialize source root '${entity.rootTypeId}' from xml '${customSourceRoot.propertiesXmlTag}': ${t.message}", t)
      elementFactory.createDummyElement()
    }
  }

  private val LOG = logger<SourceRootPropertiesHelper>()
}