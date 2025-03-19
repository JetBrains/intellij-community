// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.module

import com.intellij.java.workspace.entities.asJavaResourceRoot
import com.intellij.java.workspace.entities.asJavaSourceRoot
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.customSourceRootProperties
import org.jdom.Element
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementType
import org.jetbrains.jps.model.ex.JpsCompositeElementBase
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer
import org.jetbrains.jps.model.serialization.module.UnknownSourceRootPropertiesSerializer
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

internal class JpsModuleSourceRootBridge(private val sourceRootEntity: SourceRootEntity, parentElement: JpsElementBase<*>) 
  : JpsCompositeElementBase<JpsModuleSourceRootBridge>(), JpsTypedModuleSourceRoot<JpsElement> {
  
  init {
    parent = parentElement
  }
  
  private val rootProperties by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val javaSourcePropertiesEntity = sourceRootEntity.asJavaSourceRoot()
    if (javaSourcePropertiesEntity != null) {
      return@lazy JavaSourceRootProperties(javaSourcePropertiesEntity.packagePrefix, javaSourcePropertiesEntity.generated)
    }
    
    val javaResourcePropertiesEntity = sourceRootEntity.asJavaResourceRoot()
    if (javaResourcePropertiesEntity != null) {
      return@lazy JavaResourceRootProperties(javaResourcePropertiesEntity.relativeOutputPath, javaResourcePropertiesEntity.generated)
    }
    
    val xml = sourceRootEntity.customSourceRootProperties?.propertiesXmlTag?.let { JDOMUtil.load(it) }
    getSerializer(sourceRootEntity.rootTypeId.name).loadProperties(xml ?: Element("properties"))
  }
  
  override fun getRootType(): JpsModuleSourceRootType<JpsElement> {
    @Suppress("UNCHECKED_CAST")
    return getSerializer(sourceRootEntity.rootTypeId.name).type as JpsModuleSourceRootType<JpsElement>
  }

  override fun getProperties(): JpsElement = rootProperties

  override fun getUrl(): String = sourceRootEntity.url.url

  override fun <P : JpsElement> getProperties(type: JpsModuleSourceRootType<P>): P? {
    if (rootType == type) {
      @Suppress("UNCHECKED_CAST")
      return properties as P
    }
    return null
  }

  @Suppress("UNCHECKED_CAST")
  override fun <P : JpsElement?> getProperties(types: Set<JpsModuleSourceRootType<P>>): P? {
    if (rootType as JpsModuleSourceRootType<P> in types) {
      return properties as P
    }
    return null
  }

  override fun <P : JpsElement?> asTyped(type: JpsModuleSourceRootType<P>): JpsTypedModuleSourceRoot<P>? {
    if (rootType == type) {
      @Suppress("UNCHECKED_CAST")
      return this as JpsTypedModuleSourceRoot<P>
    }
    return null
  }

  override fun getType(): JpsElementType<*> = rootType

  override fun asTyped(): JpsTypedModuleSourceRoot<*> = this

  override fun getFile(): File = JpsPathUtil.urlToFile(url)

  override fun getPath(): Path = Paths.get(JpsPathUtil.urlToPath(url))
  
  companion object {
    val serializers by lazy(LazyThreadSafetyMode.PUBLICATION) {
      JpsModelSerializerExtension.getExtensions().flatMap { it.moduleSourceRootPropertiesSerializers }.associateBy { it.typeId }
    }
    
    fun getSerializer(typeId: String?): JpsModuleSourceRootPropertiesSerializer<*> {
      return serializers[typeId] ?: UnknownSourceRootPropertiesSerializer.forType(typeId)
    }
  }
}
