/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.config.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyFacet;
import org.jetbrains.plugins.groovy.config.GroovySDK;
import org.jetbrains.plugins.groovy.config.util.GroovySDKPointer;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;
import java.awt.event.*;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.config.ui.GroovyFacetTab");

  private GroovySDKComboBox myComboBox;
  private JButton myNewButton;
  private JPanel myPanel;
  private FacetEditorContext myEditorContext;
  private FacetValidatorsManager myValidatorsManager;

  private LibraryTable.Listener myLibraryListener;

  private String oldGroovyLibName = "";
  private String newGroovyLibName = "";


  public GroovyFacetTab(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    myNewButton.setMnemonic(KeyEvent.VK_N);
    myEditorContext = editorContext;
    myValidatorsManager = validatorsManager;
    setUpComponents();
    reset();
    myLibraryListener = new MyLibraryTableListener();
    LibraryTablesRegistrar.getInstance().getLibraryTable().addListener(myLibraryListener);
  }


  @Nls
  public String getDisplayName() {
    return GroovyBundle.message("groovy.sdk.configuration");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return !oldGroovyLibName.equals(newGroovyLibName);
  }

  public void onFacetInitialized(@NotNull Facet facet) {
    fireRootsChangedEvent();
    oldGroovyLibName = newGroovyLibName;
  }

  private void fireRootsChangedEvent() {
    final GroovySDKComboBox.DefaultGroovySDKComboBoxItem selectedItem = (GroovySDKComboBox.DefaultGroovySDKComboBoxItem) myComboBox.getSelectedItem();
    final Module module = myEditorContext.getModule();
    if (module != null) {
      final Project project = module.getProject();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          GroovySDK sdk = null;
          if (selectedItem instanceof GroovySDKComboBox.GroovySDKPointerItem) {
            GroovySDKComboBox.GroovySDKPointerItem pointerItem = (GroovySDKComboBox.GroovySDKPointerItem) selectedItem;
            String name = pointerItem.getName();
            String path = pointerItem.getPath();
            Library library = GroovyConfigUtils.createGroovyLibrary(path, name, project, true);
            if (library != null) {
              sdk = new GroovySDK(library);
            }
          } else {
            sdk = selectedItem.getGroovySDK();
          }
          GroovyConfigUtils.updateGroovyLibInModule(module, sdk);

          // create other libraries by their pointers
          for (int i = 0; i < myComboBox.getItemCount(); i++) {
            Object item = myComboBox.getItemAt(i);
            if (item != selectedItem && item instanceof GroovySDKComboBox.GroovySDKPointerItem) {
              GroovySDKComboBox.GroovySDKPointerItem pointerItem = (GroovySDKComboBox.GroovySDKPointerItem) item;
              String name = pointerItem.getName();
              String path = pointerItem.getPath();
              GroovyConfigUtils.createGroovyLibrary(path, name, project, true);
            }
          }
        }
      });
    }
  }

  public void apply() throws ConfigurationException {
    oldGroovyLibName = newGroovyLibName;
  }

  public void reset() {
    Module module = myEditorContext.getModule();
    if (module != null && FacetManager.getInstance(module).getFacetByType(GroovyFacet.ID) != null) {
      Library[] libraries = GroovyConfigUtils.getGroovyLibrariesByModule(myEditorContext.getModule());
      if (libraries.length != 1) {
        myComboBox.setSelectedIndex(0);
        oldGroovyLibName = newGroovyLibName;
      } else {
        Library library = libraries[0];
        if (library != null &&
            library.getName() != null &&
            LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraryByName(library.getName()) != null) {
          myComboBox.selectLibrary(library);
          oldGroovyLibName = newGroovyLibName;
        }
      }
    } else {
      Library library = LibrariesUtil.getLibraryByName(GroovyApplicationSettings.getInstance().DEFAULT_GROOVY_LIB_NAME);
      if (library != null) {
        myComboBox.selectLibrary(library);
      }
    }
  }

  public void disposeUIResources() {
    if (myLibraryListener != null) {
      LibraryTablesRegistrar.getInstance().getLibraryTable().removeListener(myLibraryListener);
    }
  }

  private void createUIComponents() {
    setUpComboBox();
  }

  private void setUpComponents() {

    if (myEditorContext != null && myEditorContext.getProject() != null) {
      final Project project = myEditorContext.getProject();
      myNewButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
            public boolean isFileSelectable(VirtualFile file) {
              return super.isFileSelectable(file) && GroovyConfigUtils.isGroovySdkHome(file);
            }
          };
          final FileChooserDialog fileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
          final VirtualFile[] files = fileChooserDialog.choose(null, project);
          if (files.length > 0) {
            String path = files[0].getPath();
            if (ValidationResult.OK == GroovyConfigUtils.isGroovySdkHome(path)) {
              Collection<String> versions = GroovyConfigUtils.getGroovyVersions();
              String version = GroovyConfigUtils.getGroovyVersion(path);
              boolean addVersion = !versions.contains(version) ||
                  Messages.showOkCancelDialog(GroovyBundle.message("duplicate.groovy.lib.version.add", version),
                      GroovyBundle.message("duplicate.groovy.lib.version"),
                      GroovyIcons.BIG_ICON) == 0;

              if (addVersion && !GroovyConfigUtils.UNDEFINED_VERSION.equals(version)) {
                String name = myComboBox.generatePointerName(version);
                myComboBox.addSdk(new GroovySDKPointer(name, path, version));
                newGroovyLibName = name;
              }
            } else {
              Messages.showErrorDialog(GroovyBundle.message("invalid.groovy.sdk.path.message"), GroovyBundle.message("invalid.groovy.sdk.path.text"));
            }
          }
        }
      });
    }
  }

  private void setUpComboBox() {
    myComboBox = new GroovySDKComboBox();
    myComboBox.insertItemAt(new GroovySDKComboBox.NoGroovySDKComboBoxItem(), 0);
    myComboBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        final Object o = e.getItem();
        if (o instanceof GroovySDKComboBox.GroovySDKComboBoxItem) {
          final GroovySDK sdk = ((GroovySDKComboBox.GroovySDKComboBoxItem) o).getGroovySDK();
          newGroovyLibName = sdk.getLibraryName();
        } else if (o instanceof GroovySDKComboBox.GroovySDKPointerItem) {
          final GroovySDKPointer pointer = ((GroovySDKComboBox.GroovySDKPointerItem) o).getPointer();
          newGroovyLibName = pointer.getLibraryName();
        } else {
          newGroovyLibName = "";
        }
      }
    });
    myComboBox.setSelectedIndex(0);
  }

  private void updateComboBox() {
    myComboBox.refresh();
    reset();
  }

  private class MyLibraryTableListener implements LibraryTable.Listener {

    public void afterLibraryAdded(Library newLibrary) {
      updateComboBox();
    }

    public void afterLibraryRenamed(Library library) {
      updateComboBox();
    }

    public void afterLibraryRemoved(Library library) {
      updateComboBox();
    }

    public void beforeLibraryRemoved(Library library) {

    }

  }


}
