// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties

@CompileStatic
class RepositoryProjectDescriptor extends DefaultLightProjectDescriptor {

  private final String myCoordinates

  RepositoryProjectDescriptor(String coordinates) {
    myCoordinates = coordinates
  }

  private Collection<OrderRoot> loadRoots(Project project) {
    def libraryProperties = new RepositoryLibraryProperties(myCoordinates, true)
    def roots = JarRepositoryManager.loadDependenciesModal(project, libraryProperties, false, false, null, null)
    assert !roots.isEmpty()
    return roots
  }

  @Override
  void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
    super.configureModule(module, model, contentEntry)
    def roots = loadRoots(module.project)
    def tableModel = model.moduleLibraryTable.modifiableModel
    def libraryModel = tableModel.createLibrary(myCoordinates).modifiableModel
    for (root in roots) {
      libraryModel.addRoot(root.file, root.type)
    }
    libraryModel.commit()
    tableModel.commit()
  }
}
