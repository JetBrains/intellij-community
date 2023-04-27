package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.RemoteServerConfigurable;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.util.DelayedRunner;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SingleRemoteServerConfigurable extends NamedConfigurable<RemoteServer<?>> {
  private final RemoteServerConfigurable myConfigurable;
  private final RemoteServer<?> myServer;
  private @NlsSafe String myServerName;
  private boolean myNew;
  private JPanel myMainPanel;
  private JPanel mySettingsPanel;
  private JBLabel myConnectionStatusLabel;

  private final DelayedRunner myRunner;
  private ConnectionTester myConnectionTester;

  private final RemoteServer<?> myInnerServer;
  private boolean myInnerApplied;
  private boolean myAppliedButNeedsCheck;

  private boolean myConnected;

  public <C extends ServerConfiguration> SingleRemoteServerConfigurable(RemoteServer<C> server, Runnable treeUpdater, boolean isNew) {
    super(true, treeUpdater);
    myServer = server;
    myNew = isNew;
    myServerName = myServer.getName();
    C configuration = server.getConfiguration();
    C innerConfiguration = XmlSerializerUtil.createCopy(configuration);
    myInnerServer = new RemoteServerImpl<>("<temp inner server>", server.getType(), innerConfiguration);
    myInnerApplied = false;
    myAppliedButNeedsCheck = isNew || server.getType().canAutoDetectConfiguration();

    myConfigurable = createConfigurable(server, innerConfiguration);

    myConnected = false;
    myRunner = new DelayedRunner(myMainPanel) {

      @Override
      protected boolean wasChanged() {
        if (!myConfigurable.canCheckConnection()) return false;

        boolean modified = myConfigurable.isModified();
        boolean result = modified || myAppliedButNeedsCheck;
        if (result) {
          myAppliedButNeedsCheck = false;

          setConnectionStatus(false, false, "");
          myConnectionTester = null;

          if (modified) {
            try {
              myConfigurable.apply();
              myInnerApplied = true;
            }
            catch (ConfigurationException e) {
              setConnectionStatus(true, false, e.getMessage());
            }
          }
        }
        return result;
      }

      @Override
      protected void run() {
        setConnectionStatus(false, false, CloudBundle.message("cloud.status.connecting"));

        myConnectionTester = new ConnectionTester();
        myConnectionTester.testConnection();
      }
    };
    myRunner.queueChangesCheck();
  }

  private static <C extends ServerConfiguration> RemoteServerConfigurable createConfigurable(RemoteServer<C> server, C configuration) {
    return server.getType().createServerConfigurable(configuration);
  }

  private void setConnectionStatus(boolean error, boolean connected, @NlsContexts.Label String text) {
    myConnected = connected;
    setConnectionStatusText(error, text);
  }

  protected void setConnectionStatusText(boolean error, @NlsContexts.Label String text) {
    myConnectionStatusLabel.setText(UIUtil.toHtml(text));
    myConnectionStatusLabel.setVisible(StringUtil.isNotEmpty(text));
  }

  @Override
  public RemoteServer<?> getEditableObject() {
    return myServer;
  }

  @Override
  public String getBannerSlogan() {
    return myServer.getName();
  }

  @Override
  public JComponent createOptionsPanel() {
    mySettingsPanel.add(BorderLayout.CENTER, myConfigurable.createComponent());
    return myMainPanel;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myServerName;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return myServer.getType().getHelpTopic();
  }

  @Override
  public void setDisplayName(String name) {
    myServerName = name;
  }

  @Override
  public boolean isModified() {
    return myNew || myConfigurable.isModified() || myInnerApplied || !myServerName.equals(myServer.getName());
  }

  @Override
  public void apply() throws ConfigurationException {
    boolean uncheckedApply = myConfigurable.isModified();
    myConfigurable.apply();
    XmlSerializerUtil.copyBean(myInnerServer.getConfiguration(), myServer.getConfiguration());
    myServer.setName(myServerName);
    myNew = false;
    myAppliedButNeedsCheck = uncheckedApply;
    myInnerApplied = false;
  }

  @Override
  public void reset() {
    myConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myConfigurable.disposeUIResources();
    Disposer.dispose(myRunner);
  }

  @Nullable
  @Override
  public Icon getIcon(boolean expanded) {
    return myServer.getType().getIcon();
  }

  private class ConnectionTester {
    private final RemoteServerConnectionTester myTester;

    ConnectionTester() {
      myTester = new RemoteServerConnectionTester(myInnerServer);
    }

    public void testConnection() {
      myTester.testConnection(this::testFinished);
    }

    public void testFinished(boolean connected, @NotNull String connectionStatus) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (myConnectionTester == this) {
          setConnectionStatus(!connected, connected,
                              connected ? CloudBundle.message("cloud.status.connection.successful")
                                        : CloudBundle.message("cloud.status.cannot.connect", connectionStatus));
        }
      });
    }
  }
}
