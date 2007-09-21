package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.CvsRootEditor;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsMethod;
import com.intellij.cvsSupport2.connections.CvsRootData;
import com.intellij.cvsSupport2.connections.CvsRootDataBuilder;
import com.intellij.cvsSupport2.connections.ext.ui.ExtConnectionDualPanel;
import com.intellij.cvsSupport2.connections.local.ui.LocalConnectionSettingsPanel;
import com.intellij.cvsSupport2.connections.pserver.ui.PServerSettingsPanel;
import com.intellij.cvsSupport2.connections.ssh.ui.SshConnectionSettingsPanel;
import com.intellij.cvsSupport2.connections.ui.ProxySettingsPanel;
import com.intellij.cvsSupport2.cvsExecution.ModalityContextImpl;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProviderOnEnvironment;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui.DateOrRevisionOrTagSettings;
import com.intellij.cvsSupport2.ui.CvsRootChangeListener;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.BooleanValueHolder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class Cvs2SettingsEditPanel implements CvsRootEditor {

  private JPanel myPanel;
  private final BooleanValueHolder myIsInUpdating = new BooleanValueHolder(false);
  private CvsRootAsStringConfigurationPanel myCvsRootConfigurationPanelView
    = new CvsRootAsStringConfigurationPanel(myIsInUpdating);
  private JPanel myCvsRootConfigurationPanel;

  private DateOrRevisionOrTagSettings myDateOrRevisionOrTagSettings;

  private JPanel myConnectionSettingsPanel;
  private JPanel myDateOrRevisionOrTagSettingsPanel;

  private final ExtConnectionDualPanel myExtConnectionSettingsEditor;
  private final SshConnectionSettingsPanel mySshConnectionSettingsEditor;
  private final LocalConnectionSettingsPanel myLocalConnectionSettingsPanel;

  private final PServerSettingsPanel myPServerSettingsEditor;
  private JButton myTestButton;
  @NonNls public static final String EMPTY = "EMPTY";
  private JPanel myProxySettingsPanel;
  private final ProxySettingsPanel myProxySettingsNonEmptyPanel;
  @NonNls private static final String NON_EMPTY_PROXY_SETTINGS = "NON-EMPTY-PROXY-SETTINGS";

  public Cvs2SettingsEditPanel(Project project) {
    myDateOrRevisionOrTagSettings =
    new DateOrRevisionOrTagSettings(new TagsProviderOnEnvironment() {
      @NotNull
      protected CvsEnvironment getEnv() {
        return createConfigurationWithCurrentSettings();
      }
    }, project, true);
    myPanel.setSize(myPanel.getPreferredSize());
    myCvsRootConfigurationPanel.setLayout(new BorderLayout());
    myCvsRootConfigurationPanel.add(myCvsRootConfigurationPanelView.getPanel(), BorderLayout.CENTER);

    myConnectionSettingsPanel.setLayout(new CardLayout());
    myExtConnectionSettingsEditor = new ExtConnectionDualPanel(this);
    mySshConnectionSettingsEditor = new SshConnectionSettingsPanel(this);
    myLocalConnectionSettingsPanel = new LocalConnectionSettingsPanel();
    myPServerSettingsEditor = new PServerSettingsPanel();
    myConnectionSettingsPanel.add(myExtConnectionSettingsEditor.getPanel(), CvsMethod.EXT_METHOD.getDisplayName());
    myConnectionSettingsPanel.add(myPServerSettingsEditor.getPanel(), CvsMethod.PSERVER_METHOD.getDisplayName());
    myConnectionSettingsPanel.add(mySshConnectionSettingsEditor.getPanel(), CvsMethod.SSH_METHOD.getDisplayName());
    myConnectionSettingsPanel.add(myLocalConnectionSettingsPanel.getPanel(), CvsMethod.LOCAL_METHOD.getDisplayName());

    myConnectionSettingsPanel.add(new JPanel(), EMPTY);

    myDateOrRevisionOrTagSettingsPanel.setLayout(new BorderLayout(4, 2));
    myDateOrRevisionOrTagSettingsPanel.add(myDateOrRevisionOrTagSettings.getPanel(), BorderLayout.CENTER);

    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (!myPanel.isEnabled()) return;
        testConfiguration();
      }
    });

    addCvsRootChangeListener(new CvsRootChangeListener() {
      public void onCvsRootChanged() {
        setExtPanelEnabling();
      }
    });

    myProxySettingsPanel.setLayout(new CardLayout());

    myProxySettingsNonEmptyPanel = new ProxySettingsPanel();

    myProxySettingsPanel.add(myProxySettingsNonEmptyPanel.getPanel(), NON_EMPTY_PROXY_SETTINGS);
    myProxySettingsPanel.add(new JPanel(), EMPTY);
  }

  public void addCvsRootChangeListener(CvsRootChangeListener cvsRootChangeListener) {
    myCvsRootConfigurationPanelView.addCvsRootChangeListener(cvsRootChangeListener);
  }

  private void testConfiguration() {
    testConnection();
  }

  public void updateFrom(final CvsRootConfiguration configuration) {
    setEnabled(true);
    myIsInUpdating.setValue(true);
    try {
      myCvsRootConfigurationPanelView.updateFrom(configuration);
      myExtConnectionSettingsEditor.updateFrom(configuration.EXT_CONFIGURATION, configuration.SSH_FOR_EXT_CONFIGURATION);
      mySshConnectionSettingsEditor.updateFrom(configuration.SSH_CONFIGURATION);
      myDateOrRevisionOrTagSettings.updateFrom(configuration.DATE_OR_REVISION_SETTINGS);
      myLocalConnectionSettingsPanel.updateFrom(configuration.LOCAL_CONFIGURATION);
      myProxySettingsNonEmptyPanel.updateFrom(configuration.PROXY_SETTINGS);
      myPServerSettingsEditor.updateFrom(CvsApplicationLevelConfiguration.getInstance());
    }
    finally {
      myIsInUpdating.setValue(false);
    }
    setExtPanelEnabling();
  }

  public boolean saveTo(CvsRootConfiguration configuration, boolean checkParameters) {
    try {
      myCvsRootConfigurationPanelView.saveTo(configuration, checkParameters);

      CvsApplicationLevelConfiguration globalCvsSettings = CvsApplicationLevelConfiguration.getInstance();

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
      myPServerSettingsEditor.saveTo(globalCvsSettings);
      return true;
    }
    catch (InputException ex) {
      ex.show();
      return false;
    }
  }

  public void testConnection() {
    CvsRootConfiguration newConfiguration = createConfigurationWithCurrentSettings();
    if (newConfiguration == null) return;
    testConnection(newConfiguration, myPanel);
    updateFrom(newConfiguration);
  }

  private CvsRootConfiguration createConfigurationWithCurrentSettings() {
    CvsRootConfiguration newConfiguration = CvsApplicationLevelConfiguration.createNewConfiguration(CvsApplicationLevelConfiguration.getInstance());
    if (!saveTo(newConfiguration, true)) return null;
    return newConfiguration;
  }

  public static void testConnection(CvsRootConfiguration configuration, Component component) {
    try {
      boolean loggedIn = configuration.login(new ModalityContextImpl(true));
      if (!loggedIn) return;

      configuration.testConnection();
      showSuccessfulConnectionMessage(component);
    }
    catch (ProcessCanceledException processCanceledException) {
    }
    catch (Exception e) {
      showConnectionFailedMessage(e, component);
    }
  }

  private static void showConnectionFailedMessage(Exception ex, Component component) {
    Messages.showMessageDialog(component, ex.getLocalizedMessage(), com.intellij.CvsBundle.message("operation.name.test.connection"), Messages.getErrorIcon());
  }

  private static void showSuccessfulConnectionMessage(Component component) {
    Messages.showMessageDialog(component, com.intellij.CvsBundle.message("operation.status.connection.successful"), com.intellij.CvsBundle.message("operation.name.test.connection"),
                               Messages.getInformationIcon());
  }

  public JComponent getPanel() {
    return myPanel;
  }

  private void setEnabled(boolean enabled) {
    setEnabled(myPanel, enabled);
    setExtPanelEnabling();
  }

  private void setExtPanelEnabling() {
    try {
      CvsRootData currentRootData = CvsRootDataBuilder.createSettingsOn(myCvsRootConfigurationPanelView.getCvsRoot(), true);
      String settingsPanelName = getSettingsPanelName(currentRootData);
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
    catch (Throwable ignored) {
      ((CardLayout)myConnectionSettingsPanel.getLayout()).show(myConnectionSettingsPanel, EMPTY);

      ((CardLayout)myProxySettingsPanel.getLayout()).show(myProxySettingsPanel, EMPTY);

    }
  }

  private String getProxyPanelName(CvsRootData cvsRootData) {
    if (cvsRootData.METHOD == null) {
      return EMPTY;
    }
    return cvsRootData.METHOD.supportsProxyConnection() ? NON_EMPTY_PROXY_SETTINGS : EMPTY;
  }

  private String getSettingsPanelName(CvsRootData cvsRootData) {
    CvsMethod method = cvsRootData.METHOD;
    if (method == null) {
      return EMPTY;
    }
    else {
      return method.getDisplayName();
    }
  }

  private void setEnabled(Component component, boolean enabled) {
    component.setEnabled(enabled);

    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        setEnabled(container.getComponent(i), enabled);
      }
    }
  }

  public void disable() {
    clearAllTextFields();
    setEnabled(false);
  }

  private void clearAllTextFields() {
    clearAllTextFieldsIn(myPanel);
  }

  private void clearAllTextFieldsIn(Component component) {
    if (component instanceof JTextField) {
      ((JTextField)component).setText("");
      return;
    }
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        clearAllTextFieldsIn(container.getComponent(i));
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myCvsRootConfigurationPanelView.getPreferredFocusedComponent();
  }

  public void setReadOnly() {
    myCvsRootConfigurationPanelView.setReadOnly();
    setEnabled(myDateOrRevisionOrTagSettingsPanel, false);
  }

  public String getCurrentRoot() {
    return myCvsRootConfigurationPanelView.getCvsRoot();
  }
}