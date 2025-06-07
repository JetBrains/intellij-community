// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.util.Collection;
import java.util.List;

public final class RepositoryTestLibrary implements TestLibrary {
  public RepositoryTestLibrary(String... coordinates) {
    this(DependencyScope.COMPILE, coordinates);
  }

  public RepositoryTestLibrary(String coordinates, DependencyScope dependencyScope) {
    this(dependencyScope, coordinates);
  }

  private RepositoryTestLibrary(DependencyScope dependencyScope, String... coordinates) {
    assert coordinates.length > 0;
    myCoordinates = coordinates;
    myDependencyScope = dependencyScope;
  }

  @Override
  public void addTo(@NotNull Module module, @NotNull ModifiableRootModel model) {
    final LibraryTable.ModifiableModel tableModel = model.getModuleLibraryTable().getModifiableModel();
    Library library = tableModel.createLibrary(myCoordinates[0]);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();

    for (String coordinates : myCoordinates) {
      Collection<OrderRoot> roots = loadRoots(module.getProject(), coordinates);
      for (OrderRoot root : roots) {
        libraryModel.addRoot(root.getFile(), root.getType());
      }
    }

    WriteAction.runAndWait(() -> {
      libraryModel.commit();
      tableModel.commit();
    });

    model.findLibraryOrderEntry(library).setScope(myDependencyScope);
  }

  public String[] getCoordinates() {
    return myCoordinates;
  }

  public static Collection<OrderRoot> loadRoots(Project project, String coordinates, RemoteRepositoryDescription... additional) {
    RepositoryLibraryProperties libraryProperties = new RepositoryLibraryProperties(coordinates, true);
    Collection<OrderRoot> roots = JarRepositoryManager.loadDependenciesModal(project, libraryProperties, false, false, null,
                                                                             ContainerUtil.append(getRemoteRepositoryDescriptions(), additional));
    UsefulTestCase.assertNotEmpty(roots);
    return roots;
  }

  private static List<RemoteRepositoryDescription> getRemoteRepositoryDescriptions() {
    return ContainerUtil.map(IntelliJProjectConfiguration.getRemoteRepositoryDescriptions(), repository -> {
      return new RemoteRepositoryDescription(repository.getId(), repository.getName(), repository.getUrl());
    });
  }

  private final String[] myCoordinates;
  private final DependencyScope myDependencyScope;
}
