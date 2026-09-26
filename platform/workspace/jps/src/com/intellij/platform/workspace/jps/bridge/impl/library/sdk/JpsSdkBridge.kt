// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library.sdk

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.bridge.impl.library.JpsLibraryBridgeBase
import com.intellij.platform.workspace.jps.bridge.impl.reportModificationAttempt
import com.intellij.platform.workspace.jps.entities.SdkEntity
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.ex.JpsCompositeElementBase
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.library.sdk.JpsSdkReference
import org.jetbrains.jps.model.library.sdk.JpsSdkType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer
import org.jetbrains.jps.util.JpsPathUtil

internal class JpsSdkBridge(private val sdkEntity: SdkEntity, parentElement: JpsSdkLibraryBridge) : JpsCompositeElementBase<JpsSdkBridge>(), JpsSdk<JpsElement> {
  init {
    parent = parentElement
  }
  private val properties by lazy(LazyThreadSafetyMode.PUBLICATION) {
    getSerializer(sdkEntity.type).loadProperties(JDOMUtil.load(sdkEntity.additionalData))
  }
  
  override fun getParent(): JpsLibraryBridgeBase<*> = super.getParent() as JpsLibraryBridgeBase<*>

  override fun getHomePath(): String? = sdkEntity.homePath?.url?.let { JpsPathUtil.urlToPath(it) }

  override fun getVersionString(): String? = sdkEntity.version

  @Suppress("UNCHECKED_CAST")
  override fun getSdkType(): JpsSdkType<JpsElement> = getSerializer(sdkEntity.type).type as JpsSdkType<JpsElement>

  override fun getSdkProperties(): JpsElement = properties

  override fun setHomePath(homePath: String?) {
    reportModificationAttempt()
  }

  override fun setVersionString(versionString: String?) {
    reportModificationAttempt()
  }

  override fun createReference(): JpsSdkReference<JpsElement> = JpsSdkReferenceBridge(sdkEntity.symbolicId)
  
  companion object {
    private val serializers = JpsModelSerializerExtension.getExtensions().flatMap { it.sdkPropertiesSerializers }.associateBy { it.typeId }
    
    internal fun getSerializer(typeId: String?) = serializers[typeId] ?: JpsSdkTableSerializer.JPS_JAVA_SDK_PROPERTIES_LOADER 
  }
}
