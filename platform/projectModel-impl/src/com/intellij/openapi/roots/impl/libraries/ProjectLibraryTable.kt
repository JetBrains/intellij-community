// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries

import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.Pair
import org.jdom.Element

class ProjectLibraryTable private constructor() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LibraryTable = project.service<ProjectLibraryTableInterface>()
  }

  class LibraryStateSplitter : StateSplitterEx() {
    override fun splitState(state: Element): MutableList<Pair<Element, String>> = StateSplitterEx.splitState(state, LibraryImpl.LIBRARY_NAME_ATTR)
  }
}

interface ProjectLibraryTableInterface: LibraryTable {
  val project: Project
}