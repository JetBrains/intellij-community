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

import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jdom.Element
import java.util.function.Function

class ProjectLibraryTable(project: Project) : LibraryTableBase() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LibraryTable = project.service<ProjectLibraryTable>()
  }

  private val schemeManager = SchemeManagerFactory.getInstance(project).create("libraries", object : LazySchemeProcessor<LibraryImpl, LibraryImpl>() {
    override fun createScheme(dataHolder: SchemeDataHolder<LibraryImpl>, name: String, attributeProvider: Function<String, String?>, isBundled: Boolean): LibraryImpl {
      val child = dataHolder.read().getChild("library")
      PathMacroManager.getInstance(project).expandPaths(child)
      return LibraryImpl(this@ProjectLibraryTable, child, null)
    }

    override fun isExternalizable(scheme: LibraryImpl) = true

    override fun onSchemeDeleted(scheme: LibraryImpl) {
    }

    override fun writeScheme(scheme: LibraryImpl): Element {
      val element = Element("component").setAttribute("name", "libraryTable")
      scheme.writeExternal(element)
      PathMacroManager.getInstance(project).collapsePaths(element)
      return element
    }
  }, isUseOldFileNameSanitize = true)

  init {
    schemeManager.loadSchemes()
    myModel.setLibraries(schemeManager.allSchemes)
  }

  override fun getTableLevel() = LibraryTablesRegistrar.PROJECT_LEVEL

  override fun getPresentation() = PROJECT_LIBRARY_TABLE_PRESENTATION
}

private val PROJECT_LIBRARY_TABLE_PRESENTATION = object : LibraryTablePresentation() {
  override fun getDisplayName(plural: Boolean) = ProjectBundle.message("project.library.display.name", if (plural) 2 else 1)

  override fun getDescription() = ProjectBundle.message("libraries.node.text.project")

  override fun getLibraryTableEditorTitle() = ProjectBundle.message("library.configure.project.title")
}