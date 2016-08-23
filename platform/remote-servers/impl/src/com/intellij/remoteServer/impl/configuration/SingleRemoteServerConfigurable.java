package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.RemoteServerConfigurable;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.remoteServer.util.CloudDataLoader;
import com.intellij.remoteServer.util.DelayedRunner;
import com.intellij.ui.components.JBLabel;
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
public class SingleRemoteServerConfigurable extends NamedConfigurable<RemoteServer<?>> {
  private static final String HELP_TOPIC_ID = "reference.settings.clouds";


  private final RemoteServerConfigurable myConfigurable;
  private final RemoteServer<?> myServer;
  private String myServerName;
  private boolean myNew;
  private JPanel myMainPanel;
  private JPanel mySettingsPanel;
  private JBLabel myConnectionStatusLabel;

  private final DelayedRunner myRunner;
  private ConnectionTester myConnectionTester;

  private final RemoteServer<?> myInnerServer;
  private boolean myInnerApplied;
  private boolean myUncheckedApply;

  private boolean myConnected;

  private CloudDataLoader myDataLoader = CloudDataLoader.NULL;

  public <C extends ServerConfiguration> SingleRemoteServerConfigurable(RemoteServer<C> server, Runnable treeUpdater, boolean isNew) {
    super(true, treeUpdater);
    myServer = server;
    myNew = isNew;
    myServerName = myServer.getName();
    C configuration = server.getConfiguration();
    C innerConfiguration = XmlSerializerUtil.createCopy(configuration);
    myInnerServer = new RemoteServerImpl<>("<temp inner server>", server.getType(), innerConfiguration);
    myInnerApplied = false;
    myUncheckedApply = false;

    myConfigurable = createConfigurable(server, innerConfiguration);

    myConnected = false;
    myRunner = new DelayedRunner(myMainPanel) {

      @Override
      protected boolean wasChanged() {
        if (!myConfigurable.canCheckConnection()) return false;

        boolean modified = myConfigurable.isModified();
        boolean result = modified || myUncheckedApply;
        if (result) {
          myUncheckedApply = false;

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
        setConnectionStatus(false, false, "Connecting...");

        myConnectionTester = new ConnectionTester();
        myConnectionTester.testConnection();
      }
    };
  }

  private static <C extends ServerConfiguration> RemoteServerConfigurable createConfigurable(RemoteServer<C> server, C configuration) {
    try {
      return server.getType().createServerConfigurable(configuration);
    }
    catch (UnsupportedOperationException e) {
      return new DelegatingRemoteServerConfigurable(server.getType().createConfigurable(configuration));
    }
  }

  private void setConnectionStatus(boolean error, boolean connected, String text) {
    boolean changed = myConnected != connected;
    myConnected = connected;
    setConnectionStatusText(error, text);
    if (changed) {
      notifyDataLoader();
    }
  }

  protected void setConnectionStatusText(boolean error, String text) {
    myConnectionStatusLabel.setText(UIUtil.toHtml(text));
    myConnectionStatusLabel.setVisible(StringUtil.isNotEmpty(text));
  }

  public void setDataLoader(CloudDataLoader dataLoader) {
    myDataLoader = dataLoader;
    notifyDataLoader();
  }

  private void notifyDataLoader() {
    if (myConnected) {
      myDataLoader.loadCloudData();
    }
    else {
      myDataLoader.clearCloudData();
    }
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
    Disposer.dispose(myRunner);
  }

  @Nullable
  @Override
  public Icon getIcon(boolean expanded) {
    return myServer.getType().getIcon();
  }

  private class ConnectionTester {

    public void testConnection() {
      final ServerConnection connection = ServerConnectionManager.getInstance().createTemporaryConnection(myInnerServer);
      final AtomicReference<Boolean> connectedRef = new AtomicReference<>(null);
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
          UIUtil.invokeLaterIfNeeded(() -> {
            if (myConnectionTester == ConnectionTester.this) {
              setConnectionStatus(!connected, connected, connected ? "Connection successful" : "Cannot connect: " + connection.getStatusText());
            }
          });
        }
      }.queue();
    }
  }
}
