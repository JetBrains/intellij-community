// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library.sdk

import com.intellij.platform.workspace.jps.bridge.impl.library.JpsLibraryBridgeBase
import com.intellij.platform.workspace.jps.bridge.impl.library.JpsLibraryCollectionsCache
import com.intellij.platform.workspace.jps.bridge.impl.library.JpsLibraryReferenceBridge
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkRootTypeId
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.library.JpsLibraryReference
import org.jetbrains.jps.model.library.JpsLibraryRoot
import org.jetbrains.jps.model.library.JpsLibraryType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.library.JpsLibraryRootTypeSerializer
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer

/**
 * In JPS Model SDKs are represented as regular libraries with custom properties. However, we have different entities for them in the 
 * Workspace Model, so a separate implementation is needed.  
 */
internal class JpsSdkLibraryBridge(private val sdkEntity: SdkEntity, parentElement: JpsElementBase<*>) 
  : JpsLibraryBridgeBase<JpsSdk<JpsElement>>(sdkEntity.name, parentElement) {

  private val sdkElement = JpsSdkBridge(sdkEntity, this)
  private val sdkRoots by lazy(LazyThreadSafetyMode.PUBLICATION) {
    sdkEntity.roots
      .mapNotNull { root -> root.type.asJpsOrderRootType()?.let { root to it } }
      .groupBy({ it.second }, { JpsSdkRootBridge(it.first, it.second, this) })
  }

  override fun getRoots(rootType: JpsOrderRootType): List<JpsLibraryRoot> = sdkRoots[rootType] ?: emptyList()

  override fun createReference(): JpsLibraryReference {
    //todo include sdk type to the reference to support SDKs with same name and different types?
    return JpsLibraryReferenceBridge(LibraryId(sdkEntity.name, JpsLibraryCollectionsCache.GLOBAL_LIBRARY_TABLE_ID))
  }

  @Suppress("UNCHECKED_CAST")
  override fun getType(): JpsLibraryType<JpsSdk<JpsElement>> {
    return JpsSdkBridge.getSerializer(sdkEntity.type).type as JpsLibraryType<JpsSdk<JpsElement>>
  }

  override fun getProperties(): JpsSdk<JpsElement> = sdkElement
  
  companion object {
    internal val serializers: Array<JpsLibraryRootTypeSerializer> by lazy {
      JpsSdkTableSerializer.PREDEFINED_ROOT_TYPE_SERIALIZERS +
      JpsModelSerializerExtension.getExtensions().flatMap { it.sdkRootTypeSerializers }
    }

    private val rootTypes by lazy(LazyThreadSafetyMode.PUBLICATION) {
      serializers.associateBy({ it.typeId }, { it.type })
    }

    internal fun SdkRootTypeId.asJpsOrderRootType(): JpsOrderRootType? = rootTypes[name]
  }
}
