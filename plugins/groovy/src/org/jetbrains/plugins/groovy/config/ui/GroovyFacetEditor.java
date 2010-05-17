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

package org.jetbrains.plugins.groovy.config.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.AbstractGroovyLibraryManager;
import org.jetbrains.plugins.groovy.config.LibraryManager;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author ilyas
 */
public class GroovyFacetEditor {
  @NonNls private static final String GROOVY_HOME = "GROOVY_HOME";
  private static final Comparator<Pair<Library, LibraryManager>> LIBRARY_COMPARATOR = new Comparator<Pair<Library, LibraryManager>>() {
    public int compare(Pair<Library, LibraryManager> o1, Pair<Library, LibraryManager> o2) {
      final String name1 = o1.first.getName();
      final String name2 = o2.first.getName();
      if (name1 == null || name2 == null) return 1;
      return -name1.compareToIgnoreCase(name2);
    }
  };

  private TextFieldWithBrowseButton mySdkPath;
  private JPanel myPanel;
  private JComboBox myComboBox;
  private JRadioButton myExistingSdk;
  private JRadioButton myNewSdk;
  private JLabel myNewSdkLabel;
  private final Class<? extends LibraryManager> myAcceptableManager;

  public GroovyFacetEditor(@Nullable Project project) {
    this(project, LibraryManager.class, GROOVY_HOME);
  }

  public GroovyFacetEditor(@Nullable Project project, @NotNull Class<? extends LibraryManager> acceptableManager, final String envHome) {
    myAcceptableManager = acceptableManager;
    Set<Pair<Library, LibraryManager>> libs = configureComboBox(project);
    configureSdkPathField(project);
    boolean hasVersions = !libs.isEmpty();
    myNewSdk.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        boolean status = myNewSdk.isSelected();
        mySdkPath.setEnabled(status);
        myComboBox.setEnabled(!status);
      }
    });

    if (hasVersions) {
      myExistingSdk.setSelected(true);
      mySdkPath.setEnabled(false);
      myNewSdkLabel.setVisible(false);
    } else {
      myExistingSdk.setVisible(false);
      myNewSdk.setVisible(false);
      myNewSdk.setSelected(true);
      myNewSdkLabel.setVisible(true);

      mySdkPath.setEnabled(true);

      myComboBox.setEnabled(false);
      myComboBox.setVisible(false);
    }

    final String s = System.getenv(envHome);
    if (s != null && s.length() > 0) {
      mySdkPath.setText(s);
    }
  }

  private Set<Pair<Library, LibraryManager>> configureComboBox(Project project) {
    final LibrariesContainer container = LibrariesContainerFactory.createContainer(project);
    final AbstractGroovyLibraryManager[] managers = AbstractGroovyLibraryManager.EP_NAME.getExtensions();
    Set<Pair<Library, LibraryManager>> libs = new TreeSet<Pair<Library, LibraryManager>>(LIBRARY_COMPARATOR);
    for (Library library : container.getAllLibraries()) {
      final LibraryManager manager = ManagedLibrariesEditor.findManagerFor(library, managers, container);
      if (myAcceptableManager.isInstance(manager)) {
        libs.add(Pair.create(library, manager));
      }
    }
    if (!libs.isEmpty()) {
      myComboBox.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          if (value instanceof Pair) {
            final Pair<Library, LibraryManager> pair = (Pair<Library, LibraryManager>) value;
            setIcon(pair.second.getIcon());
            setText(pair.first.getName());
          }
          return this;
        }
      });
      
      Pair<Library, LibraryManager> maxValue = libs.iterator().next();
      for (final Pair<Library, LibraryManager> lib : libs) {
        myComboBox.addItem(lib);
        final String version = lib.first.getName();
        FontMetrics fontMetrics = myComboBox.getFontMetrics(myComboBox.getFont());
        if (fontMetrics.stringWidth(version) > fontMetrics.stringWidth(maxValue.getFirst().getName())) {
          maxValue = lib;
        }
      }
      myComboBox.setPrototypeDisplayValue(maxValue);
    }
    else {
      myComboBox.setEnabled(false);
      myComboBox.setVisible(false);
    }
    return libs;
  }

  @Nullable
  public Library getSelectedLibrary() {
    final Object selectedItem = myComboBox.getSelectedItem();
    if (selectedItem != null && selectedItem instanceof Pair) {
      return ((Pair<Library,LibraryManager>)selectedItem).first;
    }
    return null;
  }

  @Nullable
  public String getNewSdkPath() {
    return mySdkPath.getText();
  }

  @Nullable
  public AbstractGroovyLibraryManager getChosenManager() {
    if (addNewSdk()) {
      final String path = getNewSdkPath();
      if (path != null) {
        final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(path));
        if (file != null && file.isDirectory()) {
          return findManager(file);
        }
      }

      return null;
    }
    final Object selectedItem = myComboBox.getSelectedItem();
    if (selectedItem != null && selectedItem instanceof Pair) {
      return (AbstractGroovyLibraryManager)((Pair)selectedItem).second;
    }
    return null;
  }

  public boolean addNewSdk() {
    return !myNewSdk.isVisible() || myNewSdk.isSelected();
  }

  private void configureSdkPathField(@Nullable final Project project) {
    mySdkPath.getButton().addActionListener(new ActionListener() {

      public void actionPerformed(final ActionEvent e) {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
          public boolean isFileSelectable(VirtualFile file) {
            if (!super.isFileSelectable(file)) {
              return false;
            }

            return findManager(file) != null;
          }
        };
        final FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
        final VirtualFile[] files = dialog.choose(null, project);
        if (files.length > 0) {
          final VirtualFile dir = files[0];
          mySdkPath.setText(FileUtil.toSystemDependentName(dir.getPath()));
        }
      }
    });

  }

  @Nullable
  private static AbstractGroovyLibraryManager findManager(VirtualFile dir) {
    if (GroovyUtils.getFilesInDirectoryByPattern(dir.getPath() + "/lib", "groovy.*\\.jar").length == 0) {
      return null;
    }

    final String name = dir.getName();

    final AbstractGroovyLibraryManager[] managers = AbstractGroovyLibraryManager.EP_NAME.getExtensions();
    for (final AbstractGroovyLibraryManager manager : managers) {
      if (manager.managesName(name) && manager.isSDKHome(dir)) {
        return manager;
      }
    }

    for (final AbstractGroovyLibraryManager manager : managers) {
      if (manager.isSDKHome(dir)) {
        return manager;
      }
    }
    return null;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private void createUIComponents() {
    myComboBox = new JComboBox() {
      public void setEnabled(boolean enabled) {
        super.setEnabled(!myNewSdk.isSelected() && enabled);
      }
    };
    mySdkPath = new TextFieldWithBrowseButton() {
      @Override
      public void setEnabled(boolean enabled) {
        super.setEnabled(addNewSdk() && enabled);
      }
    };
  }

}
