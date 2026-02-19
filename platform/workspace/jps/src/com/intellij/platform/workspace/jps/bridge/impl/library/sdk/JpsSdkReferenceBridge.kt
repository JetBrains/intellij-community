// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library.sdk

import com.intellij.platform.workspace.jps.entities.SdkId
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.library.JpsTypedLibrary
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.library.sdk.JpsSdkReference

internal class JpsSdkReferenceBridge(private val sdkId: SdkId) : JpsElementBase<JpsSdkReferenceBridge>(), JpsSdkReference<JpsElement> {
  override fun resolve(): JpsTypedLibrary<JpsSdk<JpsElement>>? {
    val model = model ?: return null
    val sdkType = JpsSdkBridge.getSerializer(sdkId.type).type
    @Suppress("UNCHECKED_CAST")
    return model.global.libraryCollection.findLibrary(sdkId.name, sdkType) as JpsTypedLibrary<JpsSdk<JpsElement>>?
  }

  override fun getSdkName(): String = sdkId.name
}
