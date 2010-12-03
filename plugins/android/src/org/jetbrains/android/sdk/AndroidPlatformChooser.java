/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 13, 2009
 * Time: 8:00:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidPlatformChooser implements Disposable {
  private final List<AndroidPlatformChooserListener> myListeners = new ArrayList<AndroidPlatformChooserListener>();

  private final AndroidLibraryManager myLibraryManager;
  private AndroidPlatformsComboBox myPlatformsComboBox;
  private JButton myNewButton;
  private JButton myEditButton;
  private JPanel myPanel;
  private JButton myRemoveButton;
  private JPanel myComboBoxWrapper;
  private JButton myViewClasspathButton;
  private JButton myRefreshButton;
  private JLabel myAndroidPlatformLabel;
  private final Project myProject;

  private AndroidPlatform myOldPlatform;

  // project is null, if we aren't inside ProjectStructure 
  public AndroidPlatformChooser(@NotNull LibraryTable.ModifiableModel model, @Nullable Project project) {
    myProject = project;
    myLibraryManager = new AndroidLibraryManager(model);
    myPlatformsComboBox = new AndroidPlatformsComboBox(myLibraryManager.getModel(), myLibraryManager.getLibraryModels());
    myComboBoxWrapper.setLayout(new BorderLayout(1, 1));
    myComboBoxWrapper.add(myPlatformsComboBox);
    myAndroidPlatformLabel.setLabelFor(myPlatformsComboBox);
    myNewButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        createNewPlatform();
      }
    });
    myRefreshButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Library library = (Library)myPlatformsComboBox.getSelectedItem();
        myPlatformsComboBox.rebuildPlatforms();
        myPlatformsComboBox.setSelectedItem(library);
      }
    });
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        AndroidPlatform platform = myPlatformsComboBox.getSelectedPlatform();
        assert platform != null;
        Library library = (Library)myPlatformsComboBox.getSelectedItem();
        AndroidPlatformEditor editor = new AndroidPlatformEditor(myPanel, platform, myLibraryManager.getModifiableModelForLibrary(library));
        editor.show();
        myPlatformsComboBox.rebuildPlatforms();
        myPlatformsComboBox.setSelectedItem(library);
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        AndroidPlatform platform = getSelectedPlatform();
        if (platform != null) {
          Library library = platform.getLibrary();
          myLibraryManager.removeLibrary(library);
          myPlatformsComboBox.removeLibrary(library);
        }
      }
    });
    if (project != null /* inside project structure */) {
      myViewClasspathButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          AndroidPlatform platform = getSelectedPlatform();
          if (platform != null) {
            Library library = platform.getLibrary();
            ProjectStructureConfigurable.getInstance(myProject).selectProjectOrGlobalLibrary(library, true);
          }
        }
      });
    }
    Disposer.register(this, myPlatformsComboBox);
    myOldPlatform = myPlatformsComboBox.getSelectedPlatform();
    myPlatformsComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateComponents();
        firePlatformChanged();
        myOldPlatform = myPlatformsComboBox.getSelectedPlatform();
      }
    });
    updateComponents();
  }

  private void createNewPlatform() {
    VirtualFile[] files = AndroidSdkUtils.chooseAndroidSdkPath(myPanel);
    if (files.length <= 0) {
      return;
    }
    assert files.length == 1;
    VirtualFile file = files[0];
    final String path = file.getPath();


    if (AndroidSdkUtils.isAndroidSdk(path)) {
      AndroidSdk sdk = AndroidSdk.parse(path, myPanel);
      chooseTargetAndCreatePlatform(sdk);
    }
    else {
      // path may be a platform/add-on path
      VirtualFile sdkDir = file.getParent();
      if (sdkDir != null) {
        sdkDir = sdkDir.getParent();
        AndroidSdk sdk = AndroidSdk.parse(sdkDir.getPath(), myPanel);
        if (sdk != null) {
          final IAndroidTarget target = sdk.findTargetByLocation(path);
          if (target != null) {
            createNewPlatform(sdk.getLocation(), target);
          }
        }
      }
    }
  }

  private void chooseTargetAndCreatePlatform(AndroidSdk sdk) {
    final IAndroidTarget[] targets = sdk.getTargets();
    if (targets.length == 0) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("no.android.targets.error"));
      return;
    }
    int selected = 0;
    if (targets.length > 1) {
      String[] targetPresentableNames = new String[targets.length];
      IAndroidTarget newerPlatform = sdk.getNewerPlatformTarget();
      String defaultSelection = null;
      for (int i = 0, targetsLength = targets.length; i < targetsLength; i++) {
        IAndroidTarget target = targets[i];
        String presentableName = AndroidSdkUtils.getPresentableTargetName(target);
        targetPresentableNames[i] = presentableName;
        if (target == newerPlatform) {
          defaultSelection = presentableName;
        }
      }
      if (defaultSelection == null) {
        defaultSelection = targetPresentableNames[0];
      }
      selected = Messages.showChooseDialog(myPanel, AndroidBundle.message("select.target.dialog.text"),
                                           AndroidBundle.message("select.target.dialog.title"), targetPresentableNames,
                                           defaultSelection, Messages.getQuestionIcon());
    }
    if (selected >= 0) {
      createNewPlatform(sdk.getLocation(), targets[selected]);
    }
  }

  private void createNewPlatform(final String sdkPath, final IAndroidTarget target) {
    Library library = ApplicationManager.getApplication().runWriteAction(new Computable<Library>() {
      public Library compute() {
        return myLibraryManager.createNewAndroidPlatform(target, sdkPath);
      }
    });
    myPlatformsComboBox.addLibrary(library);
    myPlatformsComboBox.setSelectedItem(library);
  }

  public void addListener(AndroidPlatformChooserListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(AndroidPlatformChooserListener listener) {
    myListeners.remove(listener);
  }

  private void firePlatformChanged() {
    for (AndroidPlatformChooserListener listener : myListeners) {
      listener.platformChanged(myOldPlatform);
    }
  }

  private void updateComponents() {
    boolean enabled = myPlatformsComboBox.getSelectedPlatform() != null;
    myEditButton.setEnabled(enabled);
    myRemoveButton.setEnabled(enabled);
    myViewClasspathButton.setEnabled(myProject != null && enabled);
  }

  @Nullable
  public AndroidPlatform getSelectedPlatform() {
    return myPlatformsComboBox.getSelectedPlatform();
  }

  public LibraryTable.ModifiableModel getLibraryTableModel() {
    return myPlatformsComboBox.getLibraryTableModel();
  }

  public JPanel getComponent() {
    return myPanel;
  }

  public void setSelectedPlatform(@Nullable Library platformLibrary) {
    if (platformLibrary == null) {
      myPlatformsComboBox.setSelectedItem(null);
    }
    else {
      myPlatformsComboBox.setSelectedItem(platformLibrary);
    }
  }

  public void setSelectedPlatform(@Nullable AndroidPlatform platform) {
    setSelectedPlatform(platform != null ? platform.getLibrary() : null);
  }

  public void apply() {
    myLibraryManager.apply();
  }

  public List<Library> rebuildPlatforms() {
    return myPlatformsComboBox.rebuildPlatforms();
  }

  public void dispose() {
  }
}
