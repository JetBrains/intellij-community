package com.intellij.remoteServer.util;

import com.intellij.ProjectTopics;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author michael.golubev
 */
public abstract class CloudSupportConfigurableBase<
  SC extends CloudConfigurationBase,
  DC extends CloudDeploymentNameConfiguration,
  SR extends CloudMultiSourceServerRuntimeInstance<DC, ?, ?, ?>,
  ST extends ServerType<SC>>
  extends FrameworkSupportConfigurable {

  private final ST myCloudType;
  private final Project myModelProject;

  private final CloudNotifier myNotifier;

  private boolean myInitialized = false;

  private CloudAccountSelectionEditor<SC, DC, ST> myAccountSelectionEditor;

  public CloudSupportConfigurableBase(ST cloudType, FrameworkSupportModel frameworkSupportModel) {
    myCloudType = cloudType;
    myModelProject = frameworkSupportModel.getProject();
    myNotifier = new CloudNotifier(cloudType.getPresentableName());
  }

  @Override
  public void dispose() {
    Disposer.dispose(getAccountSelectionEditor());
  }

  protected void initUI() {
    getAccountSelectionEditor().initUI();
  }

  protected void reloadExistingApplications() {
    Collection<Deployment> deployments = new ConnectionTask<Collection<Deployment>>("Loading existing applications list") {

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
    }.performSync();

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

  protected void showMessage(String message, MessageType messageType) {
    getNotifier().showMessage(message, messageType);
  }

  protected Project getProject() {
    return myModelProject;
  }

  protected RemoteServer<SC> getServer() {
    return getAccountSelectionEditor().getServer();
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

  protected CloudAccountSelectionEditor<SC, DC, ST> getAccountSelectionEditor() {
    if (myAccountSelectionEditor == null) {
      myAccountSelectionEditor = new CloudAccountSelectionEditor<SC, DC, ST>(myCloudType) {

        @Override
        protected void handleError(ConfigurationException e) {
          getNotifier().showMessage(e.getMessage(), MessageType.ERROR);
        }
      };
    }
    return myAccountSelectionEditor;
  }

  protected CloudNotifier getNotifier() {
    return myNotifier;
  }

  protected abstract JComboBox getExistingComboBox();

  protected abstract void updateApplicationUI();

  protected abstract class ConnectionTask<T> extends CloudConnectionTask<T, SC, DC, SR> {

    public ConnectionTask(String title) {
      super(myModelProject, title, CloudSupportConfigurableBase.this.getServer());
    }

    public ConnectionTask(Module module, String title) {
      super(module.getProject(), title, CloudSupportConfigurableBase.this.getServer());
    }
  }
}
