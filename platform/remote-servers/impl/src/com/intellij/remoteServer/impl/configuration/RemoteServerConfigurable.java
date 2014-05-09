package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author nik
 */
public class RemoteServerConfigurable extends NamedConfigurable<RemoteServer<?>> {

  private static final Logger LOG = Logger.getInstance("#" + RemoteServerConfigurable.class.getName());

  private static final int CHANGES_CHECK_TIME = 500;
  private static final int CONNECTION_CHECK_TIME = 2000;
  private static final int NO_CHANGES = -1;

  private final UnnamedConfigurable myConfigurable;
  private final RemoteServer<?> myServer;
  private String myServerName;
  private boolean myNew;
  private JPanel myMainPanel;
  private JPanel mySettingsPanel;
  private JBLabel myConnectionStatusLabel;

  private final Alarm myAlarm;
  private int myChangesPastTime = NO_CHANGES;
  private ConnectionTester myConnectionTester;

  private final RemoteServer<?> myInnerServer;
  private boolean myInnerApplied;
  private boolean myUncheckedApply;

  public <C extends ServerConfiguration> RemoteServerConfigurable(RemoteServer<C> server, Runnable treeUpdater, boolean isNew) {
    super(true, treeUpdater);
    myServer = server;
    myNew = isNew;
    myServerName = myServer.getName();
    C configuration = server.getConfiguration();
    C innerConfiguration = XmlSerializerUtil.createCopy(configuration);
    myInnerServer = new RemoteServerImpl<C>("<temp inner server>", server.getType(), innerConfiguration);
    myInnerApplied = false;
    myUncheckedApply = false;

    myConfigurable = server.getType().createConfigurable(innerConfiguration);

    myAlarm = new Alarm().setActivationComponent(myMainPanel);
    queueChangesCheck();
  }

  private void queueChangesCheck() {
    if (myAlarm.isDisposed()) {
      return;
    }
    myAlarm.addRequest(new Runnable() {

      @Override
      public void run() {
        checkChanges();
        queueChangesCheck();
      }
    }, CHANGES_CHECK_TIME, ModalityState.any());
  }

  private void checkChanges() {
    boolean modified = myConfigurable.isModified();
    if (modified || myUncheckedApply) {
      myUncheckedApply = false;

      setConnectionStatusText("");
      myConnectionTester = null;

      if (modified) {
        try {
          myConfigurable.apply();
          myInnerApplied = true;
        }
        catch (ConfigurationException e) {
          LOG.debug(e);
          return;
        }
      }

      myChangesPastTime = 0;
    }
    else {
      if (myChangesPastTime != NO_CHANGES) {
        myChangesPastTime += CHANGES_CHECK_TIME;
        if (myChangesPastTime >= CONNECTION_CHECK_TIME) {
          myChangesPastTime = NO_CHANGES;

          setConnectionStatusText("Connecting...");

          myConnectionTester = new ConnectionTester();
          myConnectionTester.testConnection();
        }
      }
    }
  }

  private void setConnectionStatusText(String text) {
    myConnectionStatusLabel.setText(UIUtil.toHtml(text));
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
    return null;
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
    myNew = false;
    myUncheckedApply = uncheckedApply;
    myInnerApplied = false;
  }

  @Override
  public void reset() {
    myConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myConfigurable.disposeUIResources();
    Disposer.dispose(myAlarm);
  }

  @Nullable
  @Override
  public Icon getIcon(boolean expanded) {
    return myServer.getType().getIcon();
  }

  private class ConnectionTester {

    public void testConnection() {
      final ServerConnection connection = ServerConnectionManager.getInstance().createTemporaryConnection(myInnerServer);
      final AtomicReference<Boolean> connectedRef = new AtomicReference<Boolean>(null);
      final Semaphore semaphore = new Semaphore();
      semaphore.down();
      connection.connectIfNeeded(new ServerConnector.ConnectionCallback() {

        @Override
        public void connected(@NotNull ServerRuntimeInstance serverRuntimeInstance) {
          connectedRef.set(true);
          semaphore.up();
          connection.disconnect();
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          connectedRef.set(false);
          semaphore.up();
        }
      });

      new Task.Backgroundable(null, "Connecting...", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          while (!indicator.isCanceled()) {
            if (semaphore.waitFor(500)) {
              break;
            }
          }
          final Boolean connected = connectedRef.get();
          if (connected == null) {
            return;
          }
          UIUtil.invokeLaterIfNeeded(new Runnable() {

            @Override
            public void run() {
              showConnectionStatus(connected, connection.getStatusText());
            }
          });
        }
      }.queue();
    }

    private void showConnectionStatus(boolean connected, String statusText) {
      if (myConnectionTester == this) {
        setConnectionStatusText(connected ? "Connection successful" : "Cannot connect: " + statusText);
      }
    }
  }
}
