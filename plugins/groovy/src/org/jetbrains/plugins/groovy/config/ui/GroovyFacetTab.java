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

import com.intellij.facet.ui.*;
import com.intellij.facet.Facet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovySDK;

import javax.swing.*;
import java.awt.event.*;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.config.ui.GroovyFacetTab");

  private GrovySDKComboBox myComboBox;
  private JButton myNewButton;
  private JPanel myPanel;
  private FacetEditorContext myEditorContext;
  private FacetValidatorsManager myValidatorsManager;

  private LibraryTable.Listener myLibraryListener;


  private boolean isSdkChanged = false;

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
    return isSdkChanged;
  }

  public void onFacetInitialized(@NotNull Facet facet) {
    final GrovySDKComboBox.DefaultGroovySDKComboBoxItem item = (GrovySDKComboBox.DefaultGroovySDKComboBoxItem) myComboBox.getSelectedItem();
    final Module module = myEditorContext.getModule();
    if (module != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          GroovyConfigUtils.updateGroovyLibInModule(module, item.getGroovySDK());
        }
      });
    }
    isSdkChanged = false;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
    Library[] libraries = GroovyConfigUtils.getGroovyLibrariesByModule(myEditorContext.getModule());
    if (libraries.length != 1) {
      myComboBox.setSelectedIndex(0);
      isSdkChanged = false;
    } else {
      Library library = libraries[0];
      for (int i = 0; i < myComboBox.getItemCount(); i++) {
        GrovySDKComboBox.DefaultGroovySDKComboBoxItem item = (GrovySDKComboBox.DefaultGroovySDKComboBoxItem) myComboBox.getItemAt(i);
        GroovySDK sdk = item.getGroovySDK();
        if (sdk == null) continue;
        if (library.equals(sdk.getGroovyLibrary())) {
          myComboBox.setSelectedIndex(i);
          isSdkChanged = false;
          break;
        }
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
          final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
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
                GroovyConfigUtils.createGroovyLibrary(path);
                updateComboBox();
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
    myComboBox = new GrovySDKComboBox();
    myComboBox.insertItemAt(new GrovySDKComboBox.NoGroovySDKComboBoxItem(), 0);
    myComboBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        isSdkChanged = true;
      }
    });
    myComboBox.setSelectedIndex(0);
  }

  private void updateComboBox() {
    setUpComboBox();
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
