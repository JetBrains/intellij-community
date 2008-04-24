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

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyFacetEditor {
  private TextFieldWithBrowseButton myPathToGroovy;
  private JPanel myPanel;
  private JComboBox myComboBox;
  private JCheckBox myAddNewGdkCb;
  private FacetEditorContext myEditorContext;
  private LibraryTable.Listener myLibraryListener;

  public GroovyFacetEditor(String[] versions, String defaultVersion) {
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
    LibraryTablesRegistrar.getInstance().getLibraryTable().addListener(myLibraryListener);
  }

  public String getSelectedVersion() {
    String version = null;
    if (myComboBox != null && myComboBox.getSelectedItem() != null) {
      version = myComboBox.getSelectedItem().toString();
    }
    return version;
  }

  @Nullable
  public String getNewSdkPath(){
    return myPathToGroovy.getText();
  }

  public boolean addNewSdk(){
    return myAddNewGdkCb.isSelected() && myPathToGroovy.isVisible();
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
      myPathToGroovy.setVisible(false);
    } else {
      myAddNewGdkCb.setSelected(true);
      myAddNewGdkCb.setEnabled(true);
      myAddNewGdkCb.setVisible(false);
      myComboBox.setVisible(false);
      myPathToGroovy.setEnabled(true);
      myPathToGroovy.setVisible(true);
    }

    myAddNewGdkCb.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        boolean status = myAddNewGdkCb.isSelected();
        myPathToGroovy.setEnabled(status);
        myPathToGroovy.setVisible(status);
        myPathToGroovy.setVisible(status);
        myComboBox.setEnabled(!status);
      }
    });
  }

  private void configureEditFieldForGroovyPath(final Project project) {
    myPathToGroovy.getButton().addActionListener(new ActionListener() {

      public void actionPerformed(final ActionEvent e) {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
          public boolean isFileSelectable(VirtualFile file) {
            return super.isFileSelectable(file) && GroovyConfigUtils.isGroovySdkHome(file);
          }
        };
        final FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
        final VirtualFile[] files = dialog.choose(null, project);
        if (files.length > 0) {
          String path = files[0].getPath();
          myPathToGroovy.setText(path);
        }
      }
    });

  }

  public JComponent createComponent() {
    return myPanel;
  }

  public FacetEditorContext getEditorContext() {
    return myEditorContext;
  }

  protected void setEditorContext(FacetEditorContext editorContext) {
    myEditorContext = editorContext;
  }

  private void createUIComponents() {
    myComboBox = new JComboBox(){
      public void setEnabled(boolean enabled) {
        super.setEnabled(!myAddNewGdkCb.isSelected() && enabled);
      }
    };
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
    public void afterLibraryAdded(Library library) {
      for (Library groovyLib : GroovyConfigUtils.getGroovyLibraries()) {
        if (groovyLib == library) {
          myComboBox.addItem(library.getName());
        }
      }
    }

    public void afterLibraryRenamed(Library library) {
      for (Library groovyLib : GroovyConfigUtils.getGroovyLibraries()) {
        if (groovyLib == library) {
          myComboBox.addItem(library.getName());
        }
      }
    }

    public void beforeLibraryRemoved(Library library) {
      for (Library groovyLib : GroovyConfigUtils.getGroovyLibraries()) {
        if (groovyLib == library) {
          myComboBox.removeItem(library.getName());
        }
      }

    }

    public void afterLibraryRemoved(Library library) {
    }
  }
}
