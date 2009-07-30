/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportConfigurable;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportModel;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.ui.GroovyFacetEditor;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;

/**
 * @author peter
 */
public class GroovyFacetSupportProvider extends FrameworkSupportProvider {
  protected GroovyFacetSupportProvider() {
    super("Groovy", "Groovy et al");
  }

  @NotNull
  public FrameworkSupportConfigurable createConfigurable(final @NotNull FrameworkSupportModel model) {
    final Project project = model.getProject();
    final GroovyFacetEditor facetEditor = new GroovyFacetEditor(project);
    return new FrameworkSupportConfigurable() {

      public JComponent getComponent() {
        return facetEditor.getComponent();
      }

      public void addSupport(final Module module, final ModifiableRootModel rootModel, @Nullable Library library) {
        FacetManager facetManager = FacetManager.getInstance(module);
        ModifiableFacetModel model = facetManager.createModifiableModel();
        Facet underlyingFacet = null;
        final GroovyFacetType facetType = GroovyFacetType.getInstance();
        GroovyFacet facet = facetManager.createFacet(facetType, facetType.getDefaultFacetName(), underlyingFacet);
        model.addFacet(facet);
        model.commit();

        if (!facetEditor.addNewSdk()) {
          final Library selectedLibrary = facetEditor.getSelectedLibrary();
          if (selectedLibrary != null) {
            LibrariesUtil.placeEntryToCorrectPlace(rootModel, rootModel.addLibraryEntry(selectedLibrary));
          }
          return;
        }

        final String path = facetEditor.getNewSdkPath();
        final AbstractGroovyLibraryManager libraryManager = facetEditor.getChosenManager();
        if (path != null && libraryManager != null) {
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
    };
  }

}
