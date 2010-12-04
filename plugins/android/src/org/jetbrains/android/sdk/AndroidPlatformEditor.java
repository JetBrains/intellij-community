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
import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.OrderRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 27, 2009
 * Time: 10:32:50 AM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidPlatformEditor extends DialogWrapper {
  private JPanel myPanel;
  private TextFieldWithBrowseButton mySdkPathField;
  private JComboBox myTargetCombo;
  private JPanel myLibraryEditorWrapper;
  private JTextField myNameField;
  private JLabel mySdkPathLabel;

  private final Library myLibrary;
  private final Library.ModifiableModel myLibraryModel;
  private IAndroidTarget myTarget;
  private AndroidSdk mySdk;

  @Override
  protected void doOKAction() {
    final String currentName = myLibraryModel.getName();
    String newName = myNameField.getText().trim();
    if (newName.length() == 0) {
      newName = null;
    }
    if (!Comparing.equal(newName, currentName)) {
      if (newName == null) {
        Messages.showErrorDialog(AndroidBundle.message("enter.platform.name.error"), CommonBundle.getErrorTitle());
        return;
      }
      if (myLibrary.getTable().getLibraryByName(newName) != null) {
        Messages.showErrorDialog(AndroidBundle.message("platform.already.exists.error", newName), CommonBundle.getErrorTitle());
        return;
      }
      if (AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY.equals(currentName)) {
        PropertiesComponent.getInstance().setValue(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY, newName);
      }
      myLibraryModel.setName(currentName);
    }
    super.doOKAction();
  }

  public AndroidPlatformEditor(@NotNull Component parent,
                               @NotNull AndroidPlatform platform,
                               @NotNull Library.ModifiableModel libraryModel) {
    super(parent, true);
    mySdkPathLabel.setLabelFor(mySdkPathField);
    setTitle(AndroidBundle.message("edit.platform.dialog.title", platform.getName()));
    myLibraryModel = libraryModel;
    myLibrary = platform.getLibrary();
    myTarget = platform.getTarget();
    reset(platform, parent);
    mySdkPathField.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        VirtualFile[] files = AndroidSdkUtils.chooseAndroidSdkPath(myPanel);
        if (files.length > 0) {
          assert files.length == 1;
          String newSdkPath = files[0].getPresentableUrl();
          String oldSdkPath = mySdkPathField.getText();
          if (!newSdkPath.equals(oldSdkPath)) {
            mySdkPathField.setText(newSdkPath);
          }
          clearLibrary();
          mySdk = null;
          updateTargets(myPanel);
        }
      }
    });
    myTargetCombo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        IAndroidTarget target = (IAndroidTarget)value;
        setText(AndroidSdkUtils.getPresentableTargetName(target));
        return this;
      }
    });
    myTargetCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        clearLibrary();
        fillLibrary();
      }
    });
    mySdk = platform.getSdk();
    myNameField.setText(platform.getName());

    myLibraryEditorWrapper.setLayout(new BorderLayout(1, 1));
    init();
  }

  private void clearLibrary() {
    if (myTarget != null) {
      AndroidSdk sdk = getSdk(myPanel);
      if (sdk != null) {
        List<OrderRoot> rootsToRemove = AndroidSdkUtils.getLibraryRootsForTarget(myTarget, sdk.getLocation());
        for (OrderRoot root : rootsToRemove) {
          myLibraryModel.removeRoot(root.getFile().getUrl(), root.getType());
        }
      }
    }
  }

  private void fillLibrary() {
    myTarget = getSelectedTarget();
    if (myTarget != null) {
      AndroidSdk sdk = getSdk(myPanel);
      if (sdk != null) {
        List<OrderRoot> rootsToAdd = AndroidSdkUtils.getLibraryRootsForTarget(myTarget, sdk.getLocation());
        for (OrderRoot root : rootsToAdd) {
          myLibraryModel.addRoot(root.getFile(), root.getType());
        }
      }
    }
  }

  @Nullable
  private AndroidSdk getSdk(Component component) {
    if (mySdk == null) {
      mySdk = AndroidSdk.parse(getSelectedSdkPath(), component);
    }
    return mySdk;
  }

  private void updateTargets(Component component) {
    AndroidSdk sdk = getSdk(component);
    if (sdk != null) {
      myTargetCombo.setModel(new DefaultComboBoxModel(sdk.getTargets()));
      IAndroidTarget defaultTarget = sdk.getNewerPlatformTarget();
      myTargetCombo.setSelectedItem(defaultTarget);
    }
  }

  @NotNull
  public String getSelectedSdkPath() {
    return mySdkPathField.getText().trim();
  }

  @Nullable
  public IAndroidTarget getSelectedTarget() {
    return (IAndroidTarget)myTargetCombo.getSelectedItem();
  }


  private void reset(AndroidPlatform platform, Component parent) {
    String presentableSdkPath = PathUtil.toPresentableUrl(platform.getSdk().getLocation());
    mySdkPathField.setText(presentableSdkPath);
    updateTargets(parent);
    myTargetCombo.setSelectedItem(platform.getTarget());
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
