/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.*;
import com.intellij.cvsSupport2.connections.ext.ui.ExtConnectionDualPanel;
import com.intellij.cvsSupport2.connections.local.ui.LocalConnectionSettingsPanel;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.connections.ssh.ui.SshConnectionSettingsPanel;
import com.intellij.cvsSupport2.connections.ui.ProxySettingsPanel;
import com.intellij.cvsSupport2.cvsoperations.common.LoginPerformer;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProviderOnEnvironment;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui.DateOrRevisionOrTagSettings;
import com.intellij.cvsSupport2.ui.CvsRootChangeListener;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class Cvs2SettingsEditPanel {

  private JPanel myPanel;
  private final Ref<Boolean> myIsUpdating = new Ref<>();
  private final CvsRootAsStringConfigurationPanel myCvsRootConfigurationPanelView;
  private JPanel myCvsRootConfigurationPanel;

  private final DateOrRevisionOrTagSettings myDateOrRevisionOrTagSettings;

  private JPanel myConnectionSettingsPanel;
  private JPanel myDateOrRevisionOrTagSettingsPanel;

  private final ExtConnectionDualPanel myExtConnectionSettingsEditor;
  private final SshConnectionSettingsPanel mySshConnectionSettingsEditor;
  private final LocalConnectionSettingsPanel myLocalConnectionSettingsPanel;

  private JButton myTestButton;
  @NonNls public static final String EMPTY = "EMPTY";
  private JPanel myProxySettingsPanel;
  private final ProxySettingsPanel myProxySettingsNonEmptyPanel;
  @NonNls private static final String NON_EMPTY_PROXY_SETTINGS = "NON-EMPTY-PROXY-SETTINGS";
  private final Project myProject;

  public Cvs2SettingsEditPanel(Project project, boolean readOnly) {
    myProject = project;
    myDateOrRevisionOrTagSettings = new DateOrRevisionOrTagSettings(new TagsProviderOnEnvironment() {
      @Override
      @Nullable
      protected CvsEnvironment getCvsEnvironment() {
        return createConfigurationWithCurrentSettings();
      }
    }, project);
    myPanel.setSize(myPanel.getPreferredSize());
    myCvsRootConfigurationPanel.setLayout(new BorderLayout());
    myCvsRootConfigurationPanelView = new CvsRootAsStringConfigurationPanel(readOnly, myIsUpdating);
    myCvsRootConfigurationPanel.add(myCvsRootConfigurationPanelView.getPanel(), BorderLayout.CENTER);

    myConnectionSettingsPanel.setLayout(new CardLayout());
    myExtConnectionSettingsEditor = new ExtConnectionDualPanel(project);
    mySshConnectionSettingsEditor = new SshConnectionSettingsPanel(project);
    myLocalConnectionSettingsPanel = new LocalConnectionSettingsPanel(project);
    myConnectionSettingsPanel.add(myExtConnectionSettingsEditor.getPanel(), CvsMethod.EXT_METHOD.getDisplayName());
    myConnectionSettingsPanel.add(new JPanel(), CvsMethod.PSERVER_METHOD.getDisplayName());
    myConnectionSettingsPanel.add(mySshConnectionSettingsEditor.getPanel(), CvsMethod.SSH_METHOD.getDisplayName());
    myConnectionSettingsPanel.add(myLocalConnectionSettingsPanel.getPanel(), CvsMethod.LOCAL_METHOD.getDisplayName());
    myConnectionSettingsPanel.add(new JPanel(), EMPTY);

    myDateOrRevisionOrTagSettingsPanel.setLayout(new BorderLayout(4, 2));
    myDateOrRevisionOrTagSettingsPanel.add(myDateOrRevisionOrTagSettings.getPanel(), BorderLayout.CENTER);

    myTestButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!myPanel.isEnabled()) return;
        testConfiguration();
      }
    });

    addCvsRootChangeListener(new CvsRootChangeListener() {
      @Override
      public void onCvsRootChanged() {
        conditionallyEnableComponents();
      }
    });

    myProxySettingsPanel.setLayout(new CardLayout());
    myProxySettingsNonEmptyPanel = new ProxySettingsPanel();
    myProxySettingsPanel.add(myProxySettingsNonEmptyPanel.getPanel(), NON_EMPTY_PROXY_SETTINGS);
    myProxySettingsPanel.add(new JPanel(), EMPTY);

    if (readOnly) {
      setEnabled(myDateOrRevisionOrTagSettingsPanel, false);
    }
  }

  public void addCvsRootChangeListener(CvsRootChangeListener cvsRootChangeListener) {
    myCvsRootConfigurationPanelView.addCvsRootChangeListener(cvsRootChangeListener);
    myExtConnectionSettingsEditor.addCvsRootChangeListener(cvsRootChangeListener);
  }

  public void updateFrom(final CvsRootConfiguration configuration) {
    setEnabled(true);
    myIsUpdating.set(Boolean.TRUE);
    try {
      myCvsRootConfigurationPanelView.updateFrom(configuration);
      myExtConnectionSettingsEditor.updateFrom(configuration.EXT_CONFIGURATION, configuration.SSH_FOR_EXT_CONFIGURATION);
      mySshConnectionSettingsEditor.updateFrom(configuration.SSH_CONFIGURATION);
      myDateOrRevisionOrTagSettings.updateFrom(configuration.DATE_OR_REVISION_SETTINGS);
      myLocalConnectionSettingsPanel.updateFrom(configuration.LOCAL_CONFIGURATION);
      myProxySettingsNonEmptyPanel.updateFrom(configuration.PROXY_SETTINGS);
    }
    finally {
      myIsUpdating.set(null);
    }
    conditionallyEnableComponents();
  }

  public void saveTo(CvsRootConfiguration configuration) {
    myCvsRootConfigurationPanelView.saveTo(configuration);
    final CvsApplicationLevelConfiguration globalCvsSettings = CvsApplicationLevelConfiguration.getInstance();
    if (!myExtConnectionSettingsEditor.equalsTo(configuration.EXT_CONFIGURATION, configuration.SSH_FOR_EXT_CONFIGURATION)) {
      myExtConnectionSettingsEditor.saveTo(configuration.EXT_CONFIGURATION, configuration.SSH_FOR_EXT_CONFIGURATION);
      myExtConnectionSettingsEditor.saveTo(globalCvsSettings.EXT_CONFIGURATION, globalCvsSettings.SSH_FOR_EXT_CONFIGURATION);
    }
    if (!mySshConnectionSettingsEditor.equalsTo(configuration.SSH_CONFIGURATION)) {
      mySshConnectionSettingsEditor.saveTo(configuration.SSH_CONFIGURATION);
      mySshConnectionSettingsEditor.saveTo(globalCvsSettings.SSH_CONFIGURATION);
    }
    if (!myLocalConnectionSettingsPanel.equalsTo(configuration.LOCAL_CONFIGURATION)) {
      myLocalConnectionSettingsPanel.saveTo(configuration.LOCAL_CONFIGURATION);
      myLocalConnectionSettingsPanel.saveTo(globalCvsSettings.LOCAL_CONFIGURATION);
    }
    if (!myProxySettingsNonEmptyPanel.equalsTo(configuration.PROXY_SETTINGS)) {
      myProxySettingsNonEmptyPanel.saveTo(configuration.PROXY_SETTINGS);
      myProxySettingsNonEmptyPanel.saveTo(globalCvsSettings.PROXY_SETTINGS);
    }
    myDateOrRevisionOrTagSettings.saveTo(configuration.DATE_OR_REVISION_SETTINGS);
  }

  private void testConfiguration() {
    final CvsRootConfiguration newConfiguration = createConfigurationWithCurrentSettings();
    if (newConfiguration == null) return;
    try {
      testConnection(newConfiguration, myPanel, myProject);
    } catch (CvsRootException e) {
      e.show();
      return;
    }
    updateFrom(newConfiguration);
  }

  @Nullable
  private CvsRootConfiguration createConfigurationWithCurrentSettings() {
    final CvsRootConfiguration newConfiguration =
      CvsApplicationLevelConfiguration.createNewConfiguration(CvsApplicationLevelConfiguration.getInstance());
    try {
      saveTo(newConfiguration);
    } catch (InputException e) {
      e.show();
      return null;
    }
    return newConfiguration;
  }

  private static void testConnection(final CvsRootConfiguration configuration, final Component component, Project project) {
    final CvsLoginWorker loginWorker = configuration.getLoginWorker(project);
    final Ref<Boolean> success = new Ref<>();
    ProgressManager.getInstance().run(new Task.Modal(project, CvsBundle.message("message.connecting.to.cvs.server"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText2(CvsBundle.message("message.current.global.timeout.setting",
                                             CvsApplicationLevelConfiguration.getInstance().TIMEOUT));
        try {
          final ThreeState result = LoginPerformer.checkLoginWorker(loginWorker, true);
          if (ThreeState.NO == result) {
            showConnectionFailedMessage(component, CvsBundle.message("test.connection.login.failed.text"));
          } else if (ThreeState.UNSURE == result) {
            showConnectionFailedMessage(component, CvsBundle.message("error.message.authentication.canceled"));
          } else {
            success.set(Boolean.TRUE);
          }
        }
        catch (ProcessCanceledException ignore) {}
        catch (final Exception e) {
          showConnectionFailedMessage(component, e.getLocalizedMessage());
        }
      }
    });
    if (success.get() != Boolean.TRUE) return;
    try{
      configuration.testConnection(project);
      showSuccessfulConnectionMessage(component);
    }
    catch (ProcessCanceledException ignore) {}
    catch (final Exception e) {
      showConnectionFailedMessage(component, e.getLocalizedMessage());
    }
  }

  private static void showConnectionFailedMessage(final Component parent, final String message) {
    UIUtil.invokeLaterIfNeeded(
      () -> Messages.showMessageDialog(parent, message, CvsBundle.message("operation.name.test.connection"), Messages.getErrorIcon()));
  }

  private static void showSuccessfulConnectionMessage(final Component component) {
    UIUtil.invokeLaterIfNeeded(() -> Messages.showMessageDialog(component, CvsBundle.message("operation.status.connection.successful"),
                                                            CvsBundle.message("operation.name.test.connection"), Messages.getInformationIcon()));
  }

  public JComponent getPanel() {
    return myPanel;
  }

  private void setEnabled(boolean enabled) {
    setEnabled(myPanel, enabled);
    conditionallyEnableComponents();
  }

  private void conditionallyEnableComponents() {
    try {
      final CvsRootData currentRootData = CvsRootDataBuilder.createSettingsOn(myCvsRootConfigurationPanelView.getCvsRoot(), true);
      final String settingsPanelName = getSettingsPanelName(currentRootData);
      ((CardLayout)myConnectionSettingsPanel.getLayout()).show(myConnectionSettingsPanel, settingsPanelName);
      ((CardLayout)myProxySettingsPanel.getLayout()).show(myProxySettingsPanel, getProxyPanelName(currentRootData));

      if (currentRootData.CONTAINS_PROXY_INFO) {
        myProxySettingsNonEmptyPanel.updateFrom(currentRootData);
        myProxySettingsNonEmptyPanel.disablePanel();
      }
      else {
        myProxySettingsNonEmptyPanel.enablePanel();
      }
    }
    catch (CvsRootException ignored) {
      ((CardLayout)myConnectionSettingsPanel.getLayout()).show(myConnectionSettingsPanel, EMPTY);
      ((CardLayout)myProxySettingsPanel.getLayout()).show(myProxySettingsPanel, EMPTY);
    }
  }

  private String getProxyPanelName(CvsRootData cvsRootData) {
    if (cvsRootData.METHOD == null) {
      return EMPTY;
    }
    if (cvsRootData.METHOD.supportsProxyConnection()) {
      return NON_EMPTY_PROXY_SETTINGS;
    }
    if (cvsRootData.METHOD == CvsMethod.EXT_METHOD && myExtConnectionSettingsEditor.isUseInternalSshImplementation()) {
      return NON_EMPTY_PROXY_SETTINGS;
    }
    return EMPTY;
  }

  private static String getSettingsPanelName(CvsRootData cvsRootData) {
    final CvsMethod method = cvsRootData.METHOD;
    if (method == null) {
      return EMPTY;
    }
    return method.getDisplayName();
  }

  private static void setEnabled(Component component, boolean enabled) {
    component.setEnabled(enabled);
    if (component instanceof Container) {
      final Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        setEnabled(container.getComponent(i), enabled);
      }
    }
  }

  public void disable() {
    clearAllTextFieldsIn(myPanel);
    setEnabled(false);
  }

  private static void clearAllTextFieldsIn(Component component) {
    if (component instanceof JTextField) {
      ((JTextField)component).setText("");
      return;
    }
    if (component instanceof Container) {
      final Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        clearAllTextFieldsIn(container.getComponent(i));
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myCvsRootConfigurationPanelView.getPreferredFocusedComponent();
  }
}
