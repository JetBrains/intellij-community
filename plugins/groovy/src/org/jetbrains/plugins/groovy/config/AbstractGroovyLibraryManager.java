/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.config;

import com.intellij.facet.impl.ui.ProjectConfigurableContext;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectLibrariesConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.GlobalLibrariesConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.ui.CreateLibraryDialog;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public abstract class AbstractGroovyLibraryManager extends LibraryManager {
  public static final ExtensionPointName<AbstractGroovyLibraryManager> EP_NAME = ExtensionPointName.create("org.intellij.groovy.libraryManager");

  @NotNull
  private static String generatePointerName(String version, final String libPrefix, final LibrariesContainer container,
                                            final Set<String> usedLibraryNames) {
    String originalName = libPrefix + version;
    String newName = originalName;
    int index = 1;
    while (usedLibraryNames.contains(newName)) {
      newName = originalName + " (" + index + ")";
      index++;
    }
    return newName;
  }

  @NotNull
  public String getAddActionText() {
    return "Create new " + getLibraryCategoryName() + " library...";
  }

  public Icon getDialogIcon() {
    return getIcon();
  }

  protected abstract void fillLibrary(final String path, final Library.ModifiableModel model);

  @Nullable
  public final Library createSDKLibrary(final String path,
                                  final String name,
                                  final Project project,
                                  final boolean inModuleSettings,
                                  final boolean inProject) {
    Library library;
    final Library.ModifiableModel model;
    LibraryTable.ModifiableModel globalModel = null;
    if (inModuleSettings) {
      globalModel = project != null && inProject ?
                    ProjectLibrariesConfigurable.getInstance(project).getModelProvider().getModifiableModel() :
                    GlobalLibrariesConfigurable.getInstance(project).getModelProvider().getModifiableModel();
      assert globalModel != null;
      library = globalModel.createLibrary(name);
      model = ((LibrariesModifiableModel)globalModel).getLibraryEditor(library).getModel();
    } else {
      LibraryTable table =
        project != null && inProject ? ProjectLibraryTable.getInstance(project) : LibraryTablesRegistrar.getInstance().getLibraryTable();
      library = LibraryUtil.createLibrary(table, name);
      model = library.getModifiableModel();
    }

    assert library != null;


    fillLibrary(path, model);


    if (!inModuleSettings) {
      model.commit();
    }
    else {
      globalModel.commit();
    }

    return library;
  }

  @Override
  public Library createLibrary(@NotNull FacetEditorContext context) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      public boolean isFileSelectable(VirtualFile file) {
        return super.isFileSelectable(file) && isSDKHome(file);
      }
    };
    final Project project = context.getModule().getProject();
    final VirtualFile[] files = FileChooserFactory.getInstance().createFileChooser(descriptor, project).choose(null, project);
    if (files.length == 1) {
      return createLibrary(files[0].getPath(), ((ProjectConfigurableContext)context).getContainer(), true);
    }
    return null;
  }

  @Nullable
  public Library createLibrary(@NotNull final String path, final LibrariesContainer container, final boolean inModuleSettings) {
    final List<String> versions = CollectionFactory.arrayList();
    final Set<String> usedLibraryNames = CollectionFactory.newTroveSet();
    for (Library library : container.getAllLibraries()) {
      usedLibraryNames.add(library.getName());
      if (managesLibrary(library, container)) {
        ContainerUtil.addIfNotNull(getLibraryVersion(library, container), versions);
      }
    }

    final String newVersion = getSDKVersion(path);
    final String libraryKind = getLibraryCategoryName();

    boolean addVersion = !versions.contains(newVersion) ||
                         Messages.showOkCancelDialog("Add one more " + libraryKind + " library of version " + newVersion + "?",
                                                     "Duplicate library version", getDialogIcon()) == 0;

    if (addVersion && !AbstractConfigUtils.UNDEFINED_VERSION.equals(newVersion)) {
      final Project project = container.getProject();
      final String name = generatePointerName(newVersion, getLibraryPrefix() + "-", container, usedLibraryNames);
      final CreateLibraryDialog dialog = new CreateLibraryDialog(project, "Create " + libraryKind + " library",
                                                                 "Create Project " + libraryKind + " library '" + name + "'",
                                                                 "Create Global " + libraryKind + " library '" + name + "'");
      dialog.show();
      if (dialog.isOK()) {
        return ApplicationManager.getApplication().runWriteAction(new Computable<Library>() {
          @Nullable
          public Library compute() {
            return createSDKLibrary(path, name, project, inModuleSettings, dialog.isInProject());
          }
        });
      }
    }
    return null;
  }
}
