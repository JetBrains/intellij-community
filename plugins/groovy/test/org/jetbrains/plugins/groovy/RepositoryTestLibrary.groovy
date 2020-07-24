// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.project.IntelliJProjectConfiguration
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties

@CompileStatic
final class RepositoryTestLibrary implements TestLibrary {

  private final String[] myCoordinates
  private final DependencyScope myDependencyScope

  RepositoryTestLibrary(String... coordinates) {
    this(DependencyScope.COMPILE, coordinates)
  }

  RepositoryTestLibrary(String coordinates, DependencyScope dependencyScope) {
    this(dependencyScope, coordinates)
  }

  private RepositoryTestLibrary(DependencyScope dependencyScope, String... coordinates) {
    assert coordinates.length > 0
    myCoordinates = coordinates
    myDependencyScope = dependencyScope
  }

  @Override
  void addTo(@NotNull Module module, @NotNull ModifiableRootModel model) {
    def tableModel = model.moduleLibraryTable.modifiableModel
    def library = tableModel.createLibrary(myCoordinates[0])
    def libraryModel = library.modifiableModel

    for (coordinates in myCoordinates) {
      def roots = loadRoots(module.project, coordinates)
      for (root in roots) {
        libraryModel.addRoot(root.file, root.type)
      }
    }

    WriteAction.runAndWait({
      libraryModel.commit()
      tableModel.commit()
    })

    model.findLibraryOrderEntry(library).scope = myDependencyScope
  }

  static Collection<OrderRoot> loadRoots(Project project, String coordinates) {
    def libraryProperties = new RepositoryLibraryProperties(coordinates, true)
    def roots = JarRepositoryManager.loadDependenciesModal(project, libraryProperties, false, false, null, remoteRepositoryDescriptions)
    assert !roots.isEmpty()
    return roots
  }

  private static List<RemoteRepositoryDescription> getRemoteRepositoryDescriptions() {
    IntelliJProjectConfiguration.remoteRepositoryDescriptions.collect { repository ->
      new RemoteRepositoryDescription(repository.id, repository.name, repository.url)
    }
  }
}
