package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.runtime.ConnectionStatus;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author nik
 */
public class RemoteServerConfigurable extends NamedConfigurable<RemoteServer<?>> {
  private final UnnamedConfigurable myConfigurable;
  private final RemoteServer<?> myServer;
  private String myServerName;
  private boolean myNew;
  private JPanel myMainPanel;
  private JPanel mySettingsPanel;
  private JButton myTestConnectionButton;

  public <C extends ServerConfiguration> RemoteServerConfigurable(RemoteServer<C> server, Runnable treeUpdater, boolean isNew) {
    super(true, treeUpdater);
    myServer = server;
    myNew = isNew;
    myServerName = myServer.getName();
    C c = server.getConfiguration();
    myConfigurable = server.getType().createConfigurable(c);
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
    myTestConnectionButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        try {
          myConfigurable.apply();
        }
        catch (ConfigurationException exc) {
          Messages.showErrorDialog(myMainPanel, "Cannot test connection: " + exc.getMessage(), exc.getTitle());
          return;
        }
        testConnection();
      }
    });
    return myMainPanel;
  }

  private void testConnection() {
    final ServerConnection connection = ServerConnectionManager.getInstance().getOrCreateConnection(myServer);
    final AtomicReference<Runnable> showResultRef = new AtomicReference<Runnable>(null);
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    connection.connect(new Runnable() {
      @Override
      public void run() {
        showResultRef.set(new Runnable() {
          @Override
          public void run() {
            if (connection.getStatus() == ConnectionStatus.CONNECTED) {
              Messages.showInfoMessage(myMainPanel, "Connection successful", "Test Connection");
            }
            else if (connection.getStatus() == ConnectionStatus.DISCONNECTED) {
              Messages.showErrorDialog(myMainPanel, "Cannot connect: " + connection.getStatusText(), "Test Connection");
            }
          }
        });
        semaphore.up();
      }
    });
    new Task.Modal(null, "Connecting...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        while (!indicator.isCanceled()) {
          if (semaphore.waitFor(500)) {
            break;
          }
        }
        Runnable showResult = showResultRef.get();
        if (showResult != null) {
          ApplicationManager.getApplication().invokeLater(showResult);
        }
      }
    }.queue();
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
    return myNew || myConfigurable.isModified() || !myServerName.equals(myServer.getName());
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfigurable.apply();
    myNew = false;
  }

  @Override
  public void reset() {
    myConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myConfigurable.disposeUIResources();
  }

  @Nullable
  @Override
  public Icon getIcon(boolean expanded) {
    return myServer.getType().getIcon();
  }
}
