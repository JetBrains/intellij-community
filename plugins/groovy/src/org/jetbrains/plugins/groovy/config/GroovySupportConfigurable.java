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

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.ui.GroovyFacetEditor;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;
import java.util.List;

/**
* @author peter
*/
public class GroovySupportConfigurable extends FrameworkSupportConfigurable {
  private final GroovyFacetEditor facetEditor;

  public GroovySupportConfigurable(final GroovyFacetEditor facetEditor) {
    this.facetEditor = facetEditor;
  }

  @NotNull
  public JComponent getComponent() {
    return facetEditor.getComponent();
  }

  public void addSupport(@NotNull final Module module, @NotNull final ModifiableRootModel rootModel, @Nullable Library library) {
    addGroovySupport(module, rootModel);
  }

  private boolean cleanDuplicates(List<LibraryManager> managers, ModifiableRootModel rootModel, final LibrariesContainer container) {
    if (managers.isEmpty()) return true;

    for (OrderEntry entry : rootModel.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library == null) {
          cleanUndefinedGroovyLibrary(rootModel, (LibraryOrderEntry)entry);
        } else {
          final LibraryManager manager = LibraryManager.findManagerFor(library, managers.toArray(new LibraryManager[managers.size()]), container);
          if (manager != null) {
            @SuppressWarnings({"NonConstantStringShouldBeStringBuffer"})
            String message = "There is already a " + manager.getLibraryCategoryName() + " library";
            final String version = manager.getLibraryVersion(container.getLibraryFiles(library, OrderRootType.CLASSES));
            if (StringUtil.isNotEmpty(version)) {
              message += " of version " + version;
            }
            message += ".\n Do you want to replace the existing one?";
            final String replace = "&Replace";
            final int result =
              Messages
                .showDialog(rootModel.getProject(), message, "Library already exists", new String[]{replace, "&Add", "&Cancel"}, 0, null);
            if (result == 2 || result < 0) {
              return false; //cancel or escape
            }

            if (result == 0) {
              rootModel.removeOrderEntry(entry);
            }
          }
        }
      }
    }
    return true;
  }

  private static void cleanUndefinedGroovyLibrary(ModifiableRootModel rootModel, LibraryOrderEntry entry) {
    final String libraryName = entry.getLibraryName();
    if (libraryName == null) {
      return;
    }

    for (AbstractGroovyLibraryManager each : AbstractGroovyLibraryManager.EP_NAME.getExtensions()) {
      if (each.managesName(libraryName)) {
        rootModel.removeOrderEntry(entry);
        return;
      }
    }
  }

  public void addGroovySupport(final Module module, ModifiableRootModel rootModel) {
    final LibrariesContainer container = LibrariesContainerFactory.createContainer(rootModel);

    if (!facetEditor.addNewSdk()) {
      final Library selectedLibrary = facetEditor.getSelectedLibrary();
      if (selectedLibrary != null) {
        List<LibraryManager> suitable = CollectionFactory.arrayList();
        for (final LibraryManager manager : AbstractGroovyLibraryManager.EP_NAME.getExtensions()) {
          if (manager.managesLibrary(container.getLibraryFiles(selectedLibrary, OrderRootType.CLASSES))) {
            suitable.add(manager);
          }
        }
        if (cleanDuplicates(suitable, rootModel, container)) {
          LibrariesUtil.placeEntryToCorrectPlace(rootModel, rootModel.addLibraryEntry(selectedLibrary));
        }
      }
      return;
    }

    final String path = facetEditor.getNewSdkPath();
    final AbstractGroovyLibraryManager libraryManager = facetEditor.getChosenManager();
    if (path != null && libraryManager != null) {
      List<LibraryManager> suitable = CollectionFactory.arrayList();
      suitable.add(libraryManager);
      final VirtualFile vfile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(path));
      if (vfile != null) {
        for (final LibraryManager manager : AbstractGroovyLibraryManager.EP_NAME.getExtensions()) {
          if (manager != libraryManager && manager.isSDKHome(vfile)) {
            suitable.add(manager);
          }
        }
      }

      if (!cleanDuplicates(suitable, rootModel, container)) return;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (module.isDisposed()) {
            return;
          }

          final Library lib = libraryManager.createLibrary(path, LibrariesContainerFactory.createContainer(module), false);
          if (lib != null) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
                LibrariesUtil.placeEntryToCorrectPlace(rootModel, rootModel.addLibraryEntry(lib));
                rootModel.commit();
              }
            });
          }
        }
      });
    }
  }
}
