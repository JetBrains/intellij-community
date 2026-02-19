// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl

import com.intellij.platform.workspace.jps.bridge.impl.library.JpsLibraryCollectionsCache
import com.intellij.platform.workspace.storage.EntityStorage
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties
import org.jetbrains.jps.model.impl.JpsGlobalBase
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryCollection
import org.jetbrains.jps.model.library.JpsLibraryType
import org.jetbrains.jps.model.library.JpsTypedLibrary
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.library.sdk.JpsSdkType

internal class JpsGlobalBridge(model: JpsModelBridge, globalStorage: EntityStorage) : JpsGlobalBase(model) {
  private val libraryBridgeCache by lazy(LazyThreadSafetyMode.PUBLICATION) { 
    JpsLibraryCollectionsCache(globalStorage) 
  }

  override fun getLibraryCollection(): JpsLibraryCollection {
    return libraryBridgeCache.getLibraryCollection(JpsLibraryCollectionsCache.GLOBAL_LIBRARY_TABLE_ID, this)
  } 

  override fun <P : JpsElement?, LibraryType> addLibrary(libraryType: LibraryType & Any, name: String): JpsLibrary where LibraryType : JpsLibraryType<P>?, LibraryType : JpsElementTypeWithDefaultProperties<P>? {
    reportModificationAttempt()
  }

  override fun <P : JpsElement?, SdkType> addSdk(
    name: String, homePath: String?, versionString: String?,
    type: SdkType & Any,
  ): JpsTypedLibrary<JpsSdk<P>> where SdkType : JpsSdkType<P>?, SdkType : JpsElementTypeWithDefaultProperties<P>? {
    reportModificationAttempt()
  }

  override fun <P : JpsElement?> addSdk(name: String, homePath: String?, versionString: String?, type: JpsSdkType<P>, properties: P & Any): JpsTypedLibrary<JpsSdk<P>> {
    reportModificationAttempt()
  }
}