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
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;

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

  public GroovyFacetEditor(@Nullable Project project, String defaultVersion) {
    myLibraryListener = new MyLibraryListener();
    Library[] libraries = getGroovyLibraries(project);

    if (libraries.length > 0) {
      if (defaultVersion == null) {
        defaultVersion = libraries[libraries.length - 1].getName();
      }
      adjustVersionComboBox(libraries, defaultVersion);
    } else {
      myComboBox.setEnabled(false);
      myComboBox.setVisible(false);
    }

    FacetEditorContext context = getEditorContext();
    configureEditFieldForGroovyPath(context != null ? context.getProject() : null);
    configureNewGdkCheckBox(libraries.length > 0);
    addListenerToTables(project);
  }

  private static Library[] getGroovyLibraries(final Project project) {
    final Library[] versions = GroovyConfigUtils.getInstance().getAllSDKLibraries(project);
    Arrays.sort(versions, new Comparator<Library>() {
      public int compare(Library o1, Library o2) {
        final String name1 = o1.getName();
        final String name2 = o2.getName();
        if (name1 == null || name2 == null) return 1;
        return -name1.compareToIgnoreCase(name2);
      }
    });
    return versions;
  }

  private void addListenerToTables(@Nullable final Project project) {
    LibraryTablesRegistrar.getInstance().getLibraryTable().addListener(myLibraryListener);
    if (project != null) {
      ProjectLibraryTable.getInstance(project).addListener(myLibraryListener);
    }
  }

  @Nullable
  public Library getSelectedLibrary() {
    if (myComboBox != null && myComboBox.getSelectedItem() != null && myComboBox.getSelectedItem() instanceof MyLibraryStruct) {
      return ((MyLibraryStruct)myComboBox.getSelectedItem()).library;
    }
    return null;
  }

  @Nullable
  public String getNewSdkPath() {
    return myPathToGroovy.getText();
  }

  public boolean addNewSdk() {
    return myAddNewGdkCb.isSelected() && myPathToGroovy.isVisible();
  }

  private void adjustVersionComboBox(Library[] libraries, final String defaultGlobalLibName) {
    myComboBox.removeAllItems();
    String maxValue = "";
    final MyLibraryStruct[] structs = ContainerUtil.map(libraries, new Function<Library, MyLibraryStruct>() {
      public MyLibraryStruct fun(final Library library) {
        return new MyLibraryStruct(library);
      }
    }, new MyLibraryStruct[0]);

    for (MyLibraryStruct struct : structs) {
      myComboBox.addItem(struct);
      final String version = struct.toString();
      FontMetrics fontMetrics = myComboBox.getFontMetrics(myComboBox.getFont());
      if (fontMetrics.stringWidth(version) > fontMetrics.stringWidth(maxValue)) {
        maxValue = version;
      }
    }

    myComboBox.setPrototypeDisplayValue(maxValue + "_");
    if (defaultGlobalLibName != null) {
      final MyLibraryStruct defaultStruct = ContainerUtil.find(structs, new Condition<MyLibraryStruct>() {
        public boolean value(final MyLibraryStruct struct) {
          final String name = struct.toString();
          return name != null && name.equals(defaultGlobalLibName) &&
                 LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraryByName(defaultGlobalLibName) != null;
        }
      });
      if (defaultStruct != null) {
        myComboBox.setSelectedItem(defaultStruct);
      } else if (structs.length > 0) {
        myComboBox.setSelectedItem(structs[0]);
      }
    }
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
            return super.isFileSelectable(file) && GroovyConfigUtils.getInstance().isSDKHome(file);
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
    myComboBox = new JComboBox() {
      public void setEnabled(boolean enabled) {
        super.setEnabled(!myAddNewGdkCb.isSelected() && enabled);
      }
    };
  }

  private class MyLibraryListener implements LibraryTable.Listener {
    public void afterLibraryAdded(Library library) {
      for (Library groovyLib : GroovyConfigUtils.getInstance().getGlobalSDKLibraries()) {
        if (groovyLib == library) {
          myComboBox.addItem(new MyLibraryStruct(library));
        }
      }
    }

    public void afterLibraryRenamed(Library library) {
      for (Library groovyLib : GroovyConfigUtils.getInstance().getGlobalSDKLibraries()) {
        if (groovyLib == library) {
          myComboBox.addItem(new MyLibraryStruct(library));
        }
      }
    }

    public void beforeLibraryRemoved(Library library) {
      for (Library groovyLib : GroovyConfigUtils.getInstance().getGlobalSDKLibraries()) {
        if (groovyLib == library) {
          myComboBox.removeItem(new MyLibraryStruct(library));
        }
      }

    }

    public void afterLibraryRemoved(Library library) {
    }
  }

  private static class MyLibraryStruct {
    final Library library;

    public MyLibraryStruct(final Library library) {
      this.library = library;
    }

    @Nullable
    @Override
    public String toString() {
      return library != null ? library.getName(): null;
    }
  }
}
