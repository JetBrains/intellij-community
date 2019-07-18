// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar

@State(name = "libraryTable", storages = [(Storage(value = "libraries", stateSplitter = LibraryStateSplitter::class))])
class ProjectLibraryTableImpl(val parentProject: Project) : LibraryTableBase(), ProjectLibraryTable {
  override fun getProject(): Project = parentProject

  override fun getTableLevel(): String = LibraryTablesRegistrar.PROJECT_LEVEL
  override fun getPresentation(): LibraryTablePresentation = PROJECT_LIBRARY_TABLE_PRESENTATION

}

val PROJECT_LIBRARY_TABLE_PRESENTATION = object : LibraryTablePresentation() {
  override fun getDisplayName(plural: Boolean) = ProjectBundle.message("project.library.display.name", if (plural) 2 else 1)

  override fun getDescription() = ProjectBundle.message("libraries.node.text.project")

  override fun getLibraryTableEditorTitle() = ProjectBundle.message("library.configure.project.title")
}