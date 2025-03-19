// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library

import com.intellij.platform.workspace.jps.bridge.impl.library.sdk.JpsSdkLibraryBridge
import com.intellij.platform.workspace.jps.bridge.impl.reportModificationAttempt
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryCollection
import org.jetbrains.jps.model.library.JpsLibraryType
import org.jetbrains.jps.model.library.JpsTypedLibrary

internal class JpsLibraryCollectionBridge(entities: List<LibraryEntity>, sdkEntities: List<SdkEntity>?, parentElement: JpsElementBase<*>) 
  : JpsElementBase<JpsLibraryCollectionBridge>(), JpsLibraryCollection {
  private val libraries: List<JpsLibraryBridgeBase<*>> 
  
  init {
    parent = parentElement
    val entitiesList = ArrayList<JpsLibraryBridgeBase<*>>()
    entities.mapTo(entitiesList) { entity -> JpsLibraryBridge(entity, this) }
    sdkEntities?.mapTo(entitiesList) { entity -> JpsSdkLibraryBridge(entity, this) }
    libraries = entitiesList
  }
                                            
  override fun getLibraries(): List<JpsLibrary> = libraries

  override fun <P : JpsElement> getLibraries(type: JpsLibraryType<P>): Iterable<JpsTypedLibrary<P>> {
    return libraries.asSequence()
      .filter { it.type == type }
      .filterIsInstance<JpsTypedLibrary<P>>()
      .asIterable()
  }

  override fun findLibrary(name: String): JpsLibrary? {
    return libraries.find { it.name == name }
  }

  override fun <E : JpsElement> findLibrary(name: String, type: JpsLibraryType<E>): JpsTypedLibrary<E>? {
    @Suppress("UNCHECKED_CAST")
    return libraries.find { it.name == name && it.type == type } as JpsTypedLibrary<E>?
  }

  override fun <P : JpsElement?, LibraryType> addLibrary(name: String, type: LibraryType & Any): JpsLibrary where LibraryType : JpsLibraryType<P>?, LibraryType : JpsElementTypeWithDefaultProperties<P>? {
    reportModificationAttempt()
  }

  override fun <P : JpsElement?> addLibrary(name: String, type: JpsLibraryType<P>, properties: P & Any): JpsTypedLibrary<P> {
    reportModificationAttempt()
  }

  override fun addLibrary(library: JpsLibrary) {
    reportModificationAttempt()
  }

  override fun removeLibrary(library: JpsLibrary) {
    reportModificationAttempt()
  }
}

