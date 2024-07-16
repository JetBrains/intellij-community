// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library

import com.intellij.platform.workspace.jps.entities.LibraryRoot
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryRoot
import org.jetbrains.jps.model.library.JpsOrderRootType

internal class JpsLibraryRootBridge(private val libraryRoot: LibraryRoot, parentElement: JpsLibraryBridgeBase<*>) 
  : JpsElementBase<JpsLibraryRootBridge>(), JpsLibraryRoot {
    
  init {
    parent = parentElement
  }

  override fun getRootType(): JpsOrderRootType = libraryRoot.type.asJpsOrderRootType()

  override fun getUrl(): String = libraryRoot.url.url

  override fun getInclusionOptions(): JpsLibraryRoot.InclusionOptions {
    return when (libraryRoot.inclusionOptions) {
      LibraryRoot.InclusionOptions.ROOT_ITSELF -> JpsLibraryRoot.InclusionOptions.ROOT_ITSELF
      LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT -> JpsLibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT
      LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY -> JpsLibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY
    }
  }

  override fun getLibrary(): JpsLibrary = parent as JpsLibraryBridge
}
