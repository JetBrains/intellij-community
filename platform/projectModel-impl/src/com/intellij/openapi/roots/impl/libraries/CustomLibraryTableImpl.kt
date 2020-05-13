// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries

import com.intellij.openapi.roots.libraries.LibraryTablePresentation

class CustomLibraryTableImpl(private val level: String, private val presentation: LibraryTablePresentation) : LibraryTableBase() {
  override fun getTableLevel(): String = level

  override fun getPresentation(): LibraryTablePresentation = presentation

  override fun isEditable(): Boolean = false
}