// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.bridge.impl.pathMapper
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.libraryProperties
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.library.JpsLibraryReference
import org.jetbrains.jps.model.library.JpsLibraryRoot
import org.jetbrains.jps.model.library.JpsLibraryType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.library.JpsLibraryPropertiesSerializer
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class JpsLibraryBridge(private val libraryEntity: LibraryEntity, parentElement: JpsElementBase<*>) 
  : JpsLibraryBridgeBase<JpsElement>(libraryEntity.name, parentElement) {
  
  private val libraryRoots by lazy(LazyThreadSafetyMode.PUBLICATION) { 
    libraryEntity.roots.groupBy({ it.type.asJpsOrderRootType() }, { JpsLibraryRootBridge(it, this) }) 
  }

  private val libraryProperties by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val xml = libraryEntity.libraryProperties?.propertiesXmlTag?.let { JDOMUtil.load(it) }
    getSerializer(libraryEntity.typeId?.name).loadProperties(xml, model.pathMapper)
  }
    
  override fun getRoots(rootType: JpsOrderRootType): List<JpsLibraryRoot> = libraryRoots[rootType] ?: emptyList()

  override fun createReference(): JpsLibraryReference = JpsLibraryReferenceBridge(libraryEntity.symbolicId)

  override fun getType(): JpsLibraryType<JpsElement> {
    @Suppress("UNCHECKED_CAST")
    return getSerializer(libraryEntity.typeId?.name).type as JpsLibraryType<JpsElement>
  }

  override fun getProperties(): JpsElement = libraryProperties

  companion object {
    private val serializers by lazy(LazyThreadSafetyMode.PUBLICATION) {
      JpsModelSerializerExtension.getExtensions()
        .flatMap { it.libraryPropertiesSerializers }.associateBy { it.typeId }
    }

    fun getSerializer(typeId: String?): JpsLibraryPropertiesSerializer<*> {
      return serializers[typeId] ?: JpsLibraryTableSerializer.JAVA_LIBRARY_PROPERTIES_SERIALIZER
    }
  }
}
