/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

@State(name = "libraryTable", storages = arrayOf(Storage(value = "libraries", stateSplitter = ProjectLibraryTable.LibraryStateSplitter::class)))
open class ProjectLibraryTable(val project: Project) : LibraryTableBase() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LibraryTable = project.service<ProjectLibraryTable>()
  }

  override fun getTableLevel() = LibraryTablesRegistrar.PROJECT_LEVEL

  override fun getPresentation() = PROJECT_LIBRARY_TABLE_PRESENTATION

  class LibraryStateSplitter : StateSplitterEx() {
    override fun splitState(state: Element): MutableList<Pair<Element, String>> = StateSplitterEx.splitState(state, LibraryImpl.LIBRARY_NAME_ATTR)
  }
}

private val PROJECT_LIBRARY_TABLE_PRESENTATION = object : LibraryTablePresentation() {
  override fun getDisplayName(plural: Boolean) = ProjectBundle.message("project.library.display.name", if (plural) 2 else 1)

  override fun getDescription() = ProjectBundle.message("libraries.node.text.project")

  override fun getLibraryTableEditorTitle() = ProjectBundle.message("library.configure.project.title")
}