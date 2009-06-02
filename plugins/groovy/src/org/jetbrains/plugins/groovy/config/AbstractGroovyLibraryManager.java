/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.config;

import com.intellij.facet.impl.ui.ProjectConfigurableContext;
import com.intellij.facet.ui.ProjectSettingsContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.ui.CreateLibraryDialog;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public abstract class AbstractGroovyLibraryManager extends LibraryManager {
  public static final ExtensionPointName<LibraryManager> EP_NAME = ExtensionPointName.create("org.intellij.groovy.libraryManager");

  protected static String generatePointerName(ProjectSettingsContext context, String version, AbstractConfigUtils configUtils) {
    final Set<String> usedLibraryNames = CollectionFactory.newTroveSet();
    for (Library library : getAllDefinedLibraries(((ProjectConfigurableContext)context).getContainer())) {
      usedLibraryNames.add(library.getName());
    }

    String originalName = configUtils.getSDKLibPrefix() + version;
    String newName = originalName;
    int index = 1;
    while (usedLibraryNames.contains(newName)) {
      newName = originalName + " (" + index + ")";
      index++;
    }
    return newName;
  }

  private static Library[] getAllDefinedLibraries(final LibrariesContainer container) {
    return container.getAllLibraries();
  }

  @NotNull
  public String getAddActionText() {
    return "Create new " + getLibraryCategoryName() + " library...";
  }

  protected Library createLibrary(ProjectSettingsContext context, final AbstractConfigUtils configUtils, Icon bigIcon) {
    final String libraryKind = getLibraryCategoryName();
    final Module module = context.getModule();
    final Project project = module.getProject();
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      public boolean isFileSelectable(VirtualFile file) {
        return super.isFileSelectable(file) && configUtils.isSDKHome(file);
      }
    };
    final VirtualFile[] files = FileChooserFactory.getInstance().createFileChooser(descriptor, project).choose(null, project);
    if (files.length == 1) {
      String path = files[0].getPath();
      List<String> versions = CollectionFactory.arrayList();

      final LibrariesContainer container = ((ProjectConfigurableContext)context).getContainer();
      for (Library library : getAllDefinedLibraries(container)) {
        if (managesLibrary(library, container)) {
          ContainerUtil.addIfNotNull(getLibraryVersion(library, container), versions);
        }
      }

      String newVersion = configUtils.getSDKVersion(path);

      boolean addVersion = !versions.contains(newVersion) ||
                           Messages.showOkCancelDialog("Add one more " + libraryKind + " library of version " + newVersion + "?",
                                                       "Duplicate library version", bigIcon) == 0;

      if (addVersion && !AbstractConfigUtils.UNDEFINED_VERSION.equals(newVersion)) {
        final String name = generatePointerName(context, newVersion, configUtils);
        final CreateLibraryDialog dialog = new CreateLibraryDialog(project, "Create " + libraryKind + " library",
                                                                   "Create Project " + libraryKind + " library '" + name + "'",
                                                                   "Create Global " + libraryKind + " library '" + name + "'");
        dialog.show();
        if (dialog.isOK()) {
          return configUtils.createSDKLibrary(path, name, project, true, dialog.isInProject());
        }
      }
    }
    return null;
  }
}
