// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library.sdk

import com.intellij.platform.workspace.jps.entities.SdkRoot
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryRoot
import org.jetbrains.jps.model.library.JpsOrderRootType

internal class JpsSdkRootBridge(private val sdkRoot: SdkRoot, private val sdkRootType: JpsOrderRootType, parentElement: JpsSdkLibraryBridge): JpsElementBase<JpsSdkRootBridge>(), JpsLibraryRoot {
  init {
    parent = parentElement
  }

  override fun getRootType(): JpsOrderRootType = sdkRootType

  override fun getUrl(): String = sdkRoot.url.url

  override fun getInclusionOptions(): JpsLibraryRoot.InclusionOptions = JpsLibraryRoot.InclusionOptions.ROOT_ITSELF

  override fun getLibrary(): JpsLibrary = parent as JpsLibrary
}
