// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.module

import com.intellij.platform.workspace.jps.bridge.impl.library.sdk.JpsSdkReferenceBridge
import com.intellij.platform.workspace.jps.bridge.impl.reportModificationAttempt
import com.intellij.platform.workspace.jps.entities.SdkId
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.ex.JpsCompositeElementBase
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.library.sdk.JpsSdkReference
import org.jetbrains.jps.model.library.sdk.JpsSdkType
import org.jetbrains.jps.model.module.JpsSdkReferencesTable

internal class JpsSdkReferencesTableBridge(sdkId: SdkId?, parentElement: JpsElementBase<*>) 
  : JpsCompositeElementBase<JpsSdkReferencesTableBridge>(), JpsSdkReferencesTable {
  
  private val sdkReference = sdkId?.let { 
    val reference = JpsSdkReferenceBridge(it)
    reference.parent = this
    reference
  } 
    
  init {
    parent = parentElement
  }  
    
  override fun <P : JpsElement?> getSdkReference(type: JpsSdkType<P>): JpsSdkReference<P>? {
    @Suppress("UNCHECKED_CAST")
    return sdkReference as JpsSdkReference<P>?
  }

  override fun <P : JpsElement?> setSdkReference(type: JpsSdkType<P>, sdkReference: JpsSdkReference<P>?) {
    reportModificationAttempt()
  }
}
