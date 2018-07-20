// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries

import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Pair
import org.jdom.Element

@State(name = "libraryTable", storages = [(Storage(value = "libraries", stateSplitter = ProjectLibraryTable.LibraryStateSplitter::class))])
open class ProjectLibraryTable(val project: Project) : LibraryTableBase() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LibraryTable = project.service<ProjectLibraryTable>()
  }

  override fun getTableLevel(): String = LibraryTablesRegistrar.PROJECT_LEVEL

  override fun getPresentation(): LibraryTablePresentation = PROJECT_LIBRARY_TABLE_PRESENTATION

  class LibraryStateSplitter : StateSplitterEx() {
    override fun splitState(state: Element): MutableList<Pair<Element, String>> = StateSplitterEx.splitState(state, LibraryImpl.LIBRARY_NAME_ATTR)
  }
}

private val PROJECT_LIBRARY_TABLE_PRESENTATION = object : LibraryTablePresentation() {
  override fun getDisplayName(plural: Boolean) = ProjectBundle.message("project.library.display.name", if (plural) 2 else 1)

  override fun getDescription() = ProjectBundle.message("libraries.node.text.project")

  override fun getLibraryTableEditorTitle() = ProjectBundle.message("library.configure.project.title")
}