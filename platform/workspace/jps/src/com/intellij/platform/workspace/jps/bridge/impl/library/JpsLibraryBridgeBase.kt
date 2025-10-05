// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library

import com.intellij.platform.workspace.jps.bridge.impl.reportModificationAttempt
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase
import org.jetbrains.jps.model.library.JpsLibraryRoot
import org.jetbrains.jps.model.library.JpsLibraryType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsTypedLibrary
import org.jetbrains.jps.model.library.impl.JpsLibraryRootProcessing
import java.io.File
import java.nio.file.Path

internal abstract class JpsLibraryBridgeBase<P : JpsElement>(name: String, parentElement: JpsElementBase<*>) : JpsNamedCompositeElementBase<JpsLibraryBridgeBase<P>>(name), JpsTypedLibrary<P> {
  init {
    parent = parentElement
  }

  override fun addRoot(url: String, rootType: JpsOrderRootType) {
    reportModificationAttempt()
  }

  override fun addRoot(path: Path, rootType: JpsOrderRootType) {
    reportModificationAttempt()
  }

  override fun addRoot(url: String, rootType: JpsOrderRootType, options: JpsLibraryRoot.InclusionOptions) {
    reportModificationAttempt()
  }

  override fun removeUrl(url: String, rootType: JpsOrderRootType) {
    reportModificationAttempt()
  }

  override fun delete() {
    reportModificationAttempt()
  }

  override fun <P : JpsElement?> asTyped(type: JpsLibraryType<P>): JpsTypedLibrary<P>? {
    if (getType() == type) {
      @Suppress("UNCHECKED_CAST")
      return this as JpsTypedLibrary<P>
    }
    return null
  }

  override fun getFiles(rootType: JpsOrderRootType): List<File> {
    return JpsLibraryRootProcessing.convertToFiles(getRoots(rootType)) 
  }

  override fun getPaths(rootType: JpsOrderRootType): List<Path> {
    return JpsLibraryRootProcessing.convertToPaths(getRoots(rootType))
  }

  override fun getRootUrls(rootType: JpsOrderRootType): List<String> {
    return JpsLibraryRootProcessing.convertToUrls(getRoots(rootType))
  }
}