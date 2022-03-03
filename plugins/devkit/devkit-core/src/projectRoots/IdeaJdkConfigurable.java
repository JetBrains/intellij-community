// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TextFieldWithStoredHistory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Objects;

/**
 * @author anna
 */
public class IdeaJdkConfigurable implements AdditionalDataConfigurable {
  private final JLabel mySandboxHomeLabel = new JLabel(DevKitBundle.message("sandbox.home.label"));
  private final TextFieldWithStoredHistory mySandboxHome = new TextFieldWithStoredHistory(SANDBOX_HISTORY);

  private final JLabel myInternalJreLabel = new JLabel(DevKitBundle.message("sdk.select.java.sdk.label"));
  private final DefaultComboBoxModel<Sdk> myJdksModel = new DefaultComboBoxModel<>();
  private final JComboBox<Sdk> myInternalJres = new ComboBox<>(myJdksModel);

  private Sdk myIdeaJdk;

  private boolean myModified;
  @NonNls private static final String SANDBOX_HISTORY = "DEVKIT_SANDBOX_HISTORY";

  private final SdkModel mySdkModel;
  private final SdkModificator mySdkModificator;
  private boolean myFreeze;
  private final SdkModel.Listener myListener;

  IdeaJdkConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    mySdkModel = sdkModel;
    mySdkModificator = sdkModificator;
    myListener = new SdkModel.Listener() {
      @Override
      public void sdkAdded(@NotNull Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          addJavaSdk(sdk);
        }
      }

      @Override
      public void beforeSdkRemove(@NotNull Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          removeJavaSdk(sdk);
        }
      }

      @Override
      public void sdkChanged(@NotNull Sdk sdk, String previousName) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          updateJavaSdkList(sdk, previousName);
        }
      }

      @Override
      public void sdkHomeSelected(@NotNull final Sdk sdk, @NotNull final String newSdkHome) {
        if (sdk.getSdkType() instanceof IdeaJdk) {
          internalJdkUpdate(sdk);
        }
      }
    };
    mySdkModel.addListener(myListener);
  }

  private void updateJdkList() {
    myJdksModel.removeAllElements();
    for (Sdk sdk : mySdkModel.getSdks()) {
      if (IdeaJdk.isValidInternalJdk(myIdeaJdk, sdk)) {
        myJdksModel.addElement(sdk);
      }
    }
  }

  @Override
  public void setSdk(Sdk sdk) {
    myIdeaJdk = sdk;
  }

  @Override
  public JComponent createComponent() {
    mySandboxHome.setHistorySize(5);
    JPanel wholePanel = new JPanel(new GridBagLayout());
    wholePanel.add(mySandboxHomeLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST,
                                                              GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
    wholePanel.add(GuiUtils.constructFieldWithBrowseButton(mySandboxHome, e -> {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      descriptor.setTitle(DevKitBundle.message("sandbox.home"));
      descriptor.setDescription(DevKitBundle.message("sandbox.purpose"));
      VirtualFile file = FileChooser.chooseFile(descriptor, mySandboxHome, null, null);
      if (file != null) {
        mySandboxHome.setText(FileUtil.toSystemDependentName(file.getPath()));
      }
      myModified = true;
    }), new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.EAST,
                                 GridBagConstraints.HORIZONTAL, JBUI.insets(0, 30, 0, 0), 0, 0));

    wholePanel.add(myInternalJreLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 1, GridBagConstraints.WEST,
                                                              GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
    wholePanel.add(myInternalJres, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.EAST,
                                                          GridBagConstraints.HORIZONTAL, JBUI.insets(0, 30, 0, 0), 0, 0));
    myInternalJres.setRenderer(SimpleListCellRenderer.create("", Sdk::getName));

    myInternalJres.addItemListener(e -> {
      if (myFreeze) return;
      final Sdk javaJdk = (Sdk)e.getItem();
      for (OrderRootType type : OrderRootType.getAllTypes()) {
        if (!((SdkType) javaJdk.getSdkType()).isRootTypeApplicable(type)) {
          continue;
        }
        final VirtualFile[] internalRoots = javaJdk.getRootProvider().getFiles(type);
        final VirtualFile[] configuredRoots = mySdkModificator.getRoots(type);
        for (VirtualFile file : internalRoots) {
          if (e.getStateChange() == ItemEvent.DESELECTED) {
            mySdkModificator.removeRoot(file, type);
          }
          else {
            if (ArrayUtil.find(configuredRoots, file) == -1) {
              mySdkModificator.addRoot(file, type);
            }
          }
        }
      }
    });

    mySandboxHome.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        myModified = true;
      }
    });
    mySandboxHome.addActionListener(e -> myModified = true);
    mySandboxHome.setText("");
    myModified = true;
    return wholePanel;
  }

  private void internalJdkUpdate(final Sdk sdk) {
    final Sdk javaSdk = ((Sandbox)sdk.getSdkAdditionalData()).getJavaSdk();
    if (myJdksModel.getIndexOf(javaSdk) == -1) {
      myJdksModel.addElement(javaSdk);
    }
    else {
      myJdksModel.setSelectedItem(javaSdk);
    }
  }

  @Override
  public boolean isModified() {
    return myModified;
  }

  @Override
  public void apply() {
    mySandboxHome.addCurrentTextToHistory();
    final Sandbox additionalData = (Sandbox)myIdeaJdk.getSdkAdditionalData();
    if (additionalData != null) {
      additionalData.cleanupWatchedRoots();
    }
    Sandbox sandbox = new Sandbox(mySandboxHome.getText(), (Sdk)myInternalJres.getSelectedItem(), myIdeaJdk);
    final SdkModificator modificator = myIdeaJdk.getSdkModificator();
    modificator.setSdkAdditionalData(sandbox);
    ApplicationManager.getApplication().runWriteAction(modificator::commitChanges);
    ((ProjectJdkImpl) myIdeaJdk).resetVersionString();
    myModified = false;
  }

  @Override
  public void reset() {
    myFreeze = true;
    updateJdkList();
    myFreeze = false;
    mySandboxHome.reset();
    if (myIdeaJdk != null && myIdeaJdk.getSdkAdditionalData() instanceof Sandbox) {
      final Sandbox sandbox = (Sandbox)myIdeaJdk.getSdkAdditionalData();
      final String sandboxHome = sandbox.getSandboxHome();
      mySandboxHome.setText(sandboxHome);
      mySandboxHome.setSelectedItem(sandboxHome);
      final Sdk internalJava = sandbox.getJavaSdk();
      if (internalJava != null) {
        for (int i = 0; i < myJdksModel.getSize(); i++) {
          if (Comparing.strEqual(myJdksModel.getElementAt(i).getName(), internalJava.getName())){
            myInternalJres.setSelectedIndex(i);
            break;
          }
        }
      }
      myModified = false;
    }
    else {
      mySandboxHome.setText(IdeaJdk.getDefaultSandbox());
    }
  }

  @Override
  public void disposeUIResources() {
    mySdkModel.removeListener(myListener);
  }

  private void addJavaSdk(final Sdk sdk) {
    myJdksModel.addElement(sdk);
  }

  private void removeJavaSdk(final Sdk sdk) {
    myJdksModel.removeElement(sdk);
  }

  private void updateJavaSdkList(Sdk sdk, String previousName) {
    final Sdk[] sdks = mySdkModel.getSdks();
    for (Sdk currentSdk : sdks) {
      if (currentSdk.getSdkType() instanceof IdeaJdk){
        final Sandbox sandbox = (Sandbox)currentSdk.getSdkAdditionalData();
        final Sdk internalJava = sandbox.getJavaSdk();
        if (internalJava != null && Objects.equals(internalJava.getName(), previousName)){
          sandbox.setJavaSdk(sdk);
        }
      }
    }
    updateJdkList();
  }
}
