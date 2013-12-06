package com.intellij.remoteServer.util;

import com.intellij.ProjectTopics;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.impl.configuration.RemoteServerConfigurable;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerConfigurationType;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author michael.golubev
 */
public abstract class CloudSupportConfigurableBase<
  SC extends CloudConfigurationBase,
  DC extends CloudDeploymentNameConfiguration,
  ST extends ServerType<SC>,
  SR extends CloudGitServerRuntimeInstanceBase<DC, ?, ?, ?, ?>>
  extends FrameworkSupportConfigurable {

  private final String myNotificationDisplayId;
  private final Project myModelProject;
  private RemoteServer<SC> myNewServer;
  private ST myCloudType;
  private RemoteServerConfigurable myServerConfigurable;
  private JPanel myServerConfigurablePanel;

  private boolean myInitialized = false;

  public CloudSupportConfigurableBase(FrameworkSupportModel frameworkSupportModel, ST cloudType, String notificationDisplayId) {
    myModelProject = frameworkSupportModel.getProject();
    myCloudType = cloudType;
    myNotificationDisplayId = notificationDisplayId;
  }

  @Override
  public void dispose() {
    myServerConfigurable.disposeUIResources();
  }

  protected void initUI() {
    JComboBox serverComboBox = getServerComboBox();

    serverComboBox.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        onAccountSelectionChanged();
      }
    });

    for (RemoteServer<SC> server : RemoteServersManager.getInstance().getServers(myCloudType)) {
      serverComboBox.addItem(new ServerItem(server));
    }
    serverComboBox.addItem(new ServerItem(myNewServer));
  }

  protected void reloadExistingApplications() {
    Collection<Deployment> deployments = new ConnectionTask<Collection<Deployment>>("Loading existing applications list", true, true) {

      @Override
      protected void run(final ServerConnection<DC> connection,
                         final Semaphore semaphore,
                         final AtomicReference<Collection<Deployment>> result) {
        connection.connectIfNeeded(new ServerConnector.ConnectionCallback<DC>() {

          @Override
          public void connected(@NotNull ServerRuntimeInstance<DC> serverRuntimeInstance) {
            connection.computeDeployments(new Runnable() {

              @Override
              public void run() {
                result.set(connection.getDeployments());
                semaphore.up();
              }
            });
          }

          @Override
          public void errorOccurred(@NotNull String errorMessage) {
            runtimeErrorOccurred(errorMessage);
            semaphore.up();
          }
        });
      }

      @Override
      protected Collection<Deployment> run(SR serverRuntimeInstance) throws ServerRuntimeException {
        return null;
      }
    }.perform();

    if (deployments == null) {
      return;
    }

    JComboBox existingComboBox = getExistingComboBox();
    existingComboBox.removeAllItems();
    for (Deployment deployment : deployments) {
      existingComboBox.addItem(deployment.getName());
    }
  }

  @Override
  public void onFrameworkSelectionChanged(boolean selected) {
    if (selected && !myInitialized) {
      myInitialized = true;
      initUI();
      updateApplicationUI();
    }
  }

  private ServerItem getSelectedServerItem() {
    return (ServerItem)getServerComboBox().getSelectedItem();
  }

  private void onAccountSelectionChanged() {
    myServerConfigurablePanel.setVisible(getSelectedServerItem().isNew());
  }

  protected JPanel createServerConfigurablePanel() {
    myNewServer = RemoteServersManager.getInstance().createServer(myCloudType, generateServerName());
    myServerConfigurable = new RemoteServerConfigurable(myNewServer, null, true);
    myServerConfigurablePanel = (JPanel)myServerConfigurable.createComponent();
    return myServerConfigurablePanel;
  }

  protected void showMessage(String message, MessageType messageType) {
    NotificationGroup notificationGroup = NotificationGroup.balloonGroup(myNotificationDisplayId);
    Notification notification = notificationGroup.createNotification(message, messageType);
    notification.notify(null);
  }

  protected void showErrorMessage(String errorMessage) {
    showMessage(errorMessage, MessageType.ERROR);
  }

  private String generateServerName() {
    return UniqueNameGenerator.generateUniqueName(myCloudType.getPresentableName(), new Condition<String>() {

      @Override
      public boolean value(String s) {
        for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers()) {
          if (server.getName().equals(s)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  private DeployToServerConfigurationType getRunConfigurationType() {
    String id = DeployToServerConfigurationType.getId(myCloudType);
    for (ConfigurationType configurationType : ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions()) {
      if (configurationType instanceof DeployToServerConfigurationType) {
        DeployToServerConfigurationType deployConfigurationType = (DeployToServerConfigurationType)configurationType;
        if (deployConfigurationType.getId().equals(id)) {
          return deployConfigurationType;
        }
      }
    }
    return null;
  }

  protected RemoteServer<SC> getServer() {
    ServerItem serverItem = getSelectedServerItem();
    if (serverItem.isNew()) {
      try {
        myServerConfigurable.apply();
        myNewServer.setName(myServerConfigurable.getDisplayName());
      }
      catch (ConfigurationException e) {
        showErrorMessage(e.getMessage());
        return null;
      }
    }
    return serverItem.getServer();
  }

  protected DeployToServerRunConfiguration<SC, DC> createRunConfiguration(String name, Module module, DC deploymentConfiguration) {
    Project project = module.getProject();

    RemoteServer<SC> server = getServer();

    if (getSelectedServerItem().isNew()) {
      RemoteServersManager.getInstance().addServer(server);
    }

    String serverName = server.getName();

    final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    final RunnerAndConfigurationSettings runSettings
      = runManager.createRunConfiguration(name, getRunConfigurationType().getConfigurationFactories()[0]);

    final DeployToServerRunConfiguration<SC, DC> result = (DeployToServerRunConfiguration<SC, DC>)runSettings.getConfiguration();

    runManager.addConfiguration(runSettings, false);
    runManager.setSelectedConfiguration(runSettings);

    result.setServerName(serverName);

    final ModulePointer modulePointer = ModulePointerManager.getInstance(project).create(module);
    result.setDeploymentSource(new ModuleDeploymentSourceImpl(modulePointer));

    result.setDeploymentConfiguration(deploymentConfiguration);

    return result;
  }

  protected void runOnModuleAdded(final Module module, final Runnable runnable) {
    if (myModelProject == null) {
      StartupManager.getInstance(module.getProject()).runWhenProjectIsInitialized(runnable);
    }
    else {
      MessageBusConnection connection = myModelProject.getMessageBus().connect(myModelProject);
      connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {

        public void moduleAdded(Project project, Module addedModule) {
          if (addedModule == module) {
            runnable.run();
          }
        }
      });
    }
  }

  protected abstract JComboBox getExistingComboBox();

  protected abstract JComboBox getServerComboBox();

  protected abstract void updateApplicationUI();

  protected class ServerItem {

    private final RemoteServer<SC> myServer;

    public ServerItem(RemoteServer<SC> server) {
      myServer = server;
    }

    public boolean isNew() {
      return myServer == myNewServer;
    }

    public RemoteServer<SC> getServer() {
      return myServer;
    }

    @Override
    public String toString() {
      return isNew() ? "New account..." : myServer.getName();
    }
  }

  protected abstract class ConnectionTask<T> extends CloudConnectionTask<T, SC, DC, SR> {

    public ConnectionTask(String title, boolean modal, boolean cancellable) {
      super(myModelProject, title, modal, cancellable);
    }

    public ConnectionTask(Module module, String title, boolean modal, boolean cancellable) {
      super(module.getProject(), title, modal, cancellable);
    }

    @Override
    protected RemoteServer<SC> getServer() {
      return CloudSupportConfigurableBase.this.getServer();
    }
  }
}
