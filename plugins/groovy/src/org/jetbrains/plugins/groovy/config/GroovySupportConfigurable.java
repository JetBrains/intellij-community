package org.jetbrains.plugins.groovy.config;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
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

  public void addGroovySupport(final Module module, ModifiableRootModel rootModel) {
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
}
