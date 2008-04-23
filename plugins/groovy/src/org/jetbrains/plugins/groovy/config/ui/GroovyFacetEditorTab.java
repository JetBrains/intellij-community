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
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GroovyFacetEditorTab extends FacetEditorTab {
  private TextFieldWithBrowseButton myPathToGroovy;
  private JPanel myPanel;
  private JComboBox myComboBox;
  private JCheckBox myAddNewGdkCb;
  private FacetEditorContext myEditorContext;
  private FacetValidatorsManager myValidatorsManager;
  private LibraryTable.Listener myLibraryListener;

  public static GroovyFacetEditorTab createEditorTab(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();

    String defaultVersion = getGroovyLibVersion(editorContext);
    if (defaultVersion == null || !settings.GROOVY_VERSIONS.contains(defaultVersion)) {
      defaultVersion = settings.DEFAULT_GROOVY_VERSION;
    }

    String[] versions = settings.GROOVY_VERSIONS.toArray(new String[settings.GROOVY_VERSIONS.size()]);
    GroovyFacetEditorTab tab = new GroovyFacetEditorTab(versions, defaultVersion, validatorsManager);
    tab.setEditorContext(editorContext);
    return tab;
  }

  public GroovyFacetEditorTab(String[] versions, String defaultVersion, FacetValidatorsManager validatorsManager) {
    myValidatorsManager = validatorsManager;
    myLibraryListener = new MyLibraryListener();

    if (versions.length > 0) {
      if (defaultVersion == null) {
        defaultVersion = versions[versions.length - 1];
      }
      adjustVersionComboBox(versions, defaultVersion);
    } else {
      myComboBox.setEnabled(false);
      myComboBox.setVisible(false);
    }

    FacetEditorContext context = getEditorContext();
    configureEditFieldForGroovyPath(context != null ? context.getProject() : null);
    configureNewGdkCheckBox(versions.length > 0);

    if (validatorsManager != null) {
      validatorsManager.registerValidator(new MyFacetEditorValidator(), myPathToGroovy);
    }
  }

  private void adjustVersionComboBox(String[] versions, String defaultVersion) {
    myComboBox.removeAllItems();
    String maxValue = "";
    for (String version : versions) {
      myComboBox.addItem(version);
      FontMetrics fontMetrics = myComboBox.getFontMetrics(myComboBox.getFont());
      if (fontMetrics.stringWidth(version) > fontMetrics.stringWidth(maxValue)) {
        maxValue = version;
      }
    }
    myComboBox.setPrototypeDisplayValue(maxValue + "_");
    myComboBox.setSelectedItem(defaultVersion);
  }


  private void configureNewGdkCheckBox(boolean hasVersions) {
    myAddNewGdkCb.setEnabled(true);
    if (hasVersions) {
      myAddNewGdkCb.setSelected(false);
      myAddNewGdkCb.setVisible(true);
      myPathToGroovy.setEnabled(false);
    } else {
      myAddNewGdkCb.setSelected(true);
      myAddNewGdkCb.setVisible(false);
      myAddNewGdkCb.setEnabled(true);
    }

    myAddNewGdkCb.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        myPathToGroovy.setEnabled(myAddNewGdkCb.isSelected());
      }
    });
  }

  private void configureEditFieldForGroovyPath(final Project project) {
    myPathToGroovy.getButton().addActionListener(new ActionListener() {

      public void actionPerformed(final ActionEvent e) {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        final FileChooserDialog fileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
        final VirtualFile[] files = fileChooserDialog.choose(null, project);
        if (files.length > 0) {
          String path = files[0].getPath();
          myPathToGroovy.setText(path);
          ValidationResult validationResult = new MyFacetEditorValidator().check();
          if (validationResult == ValidationResult.OK) {
            ArrayList<String> versions = GroovyApplicationSettings.getInstance().GROOVY_VERSIONS;
            String version = GroovyConfigUtils.getGroovyVersion(path);
            boolean addVersion = !versions.contains(version) ||
                Messages.showOkCancelDialog(GroovyBundle.message("duplicate.groovy.lib.version.add", version),
                    GroovyBundle.message("duplicate.groovy.lib.version"),
                    GroovyIcons.BIG_ICON) == 0;

            if (addVersion && !GroovyConfigUtils.UNDEFINED_VERSION.equals(version)) {
              GroovyConfigUtils.createGroovyLibrary(path, null, project);
              adjustVersionComboBox(versions.toArray(new String[versions.size()]), version);
            }
          }
        }
      }

    });

  }

  @Nls
  public String getDisplayName() {
    return GroovyBundle.message("file.template.group.title.groovy");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {

  }

  public void reset() {

  }

  public void disposeUIResources() {

  }

  public FacetEditorContext getEditorContext() {
    return myEditorContext;
  }

  protected void setEditorContext(FacetEditorContext editorContext) {
    myEditorContext = editorContext;
  }

  private void createUIComponents() {
    // custom component creation code
  }

  private static String getGroovyLibVersion(FacetEditorContext editorContext) {
    Module module = editorContext.getModule();
    if (module == null) return null;
    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    LibraryTable table = model.getModuleLibraryTable();
    for (Library library : table.getLibraries()) {
      String name = library.getName();
      for (String version : GroovyApplicationSettings.getInstance().GROOVY_VERSIONS) {
        if ((GroovyConfigUtils.GROOVY_LIB_PREFIX + version).equals(name)) {
          return version;
        }
      }
    }
    return null;
  }

  public FacetValidatorsManager getValidatorsManager() {
    return myValidatorsManager;
  }

  private class MyFacetEditorValidator extends FacetEditorValidator {
    public ValidationResult check() {
      if (myAddNewGdkCb.isEnabled()) {
        final Object o = myPathToGroovy.getTextField().getText();
        if (o != null) {
          final VirtualFile relativeFile = VfsUtil.findRelativeFile(o.toString(), null);
          if (relativeFile != null && GroovyConfigUtils.isGroovySdkHome(relativeFile)) return ValidationResult.OK;
        }
        return new ValidationResult(GroovyBundle.message("invalid.groovy.sdk.path.message"));
      }
      return ValidationResult.OK;
    }
  }

  private class MyLibraryListener implements LibraryTable.Listener {
    public void afterLibraryAdded(Library newLibrary) {

    }

    public void afterLibraryRenamed(Library library) {

    }

    public void beforeLibraryRemoved(Library library) {
      String name = library.getName();
      GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();
      for (String version : settings.GROOVY_VERSIONS) {
        if (GroovyConfigUtils.getLibNameByVersion(version).equals(name)) {
          myComboBox.removeItem(version);
        }
      }
    }

    public void afterLibraryRemoved(Library library) {
    }
  }
}
