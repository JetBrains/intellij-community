// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNodeSelector;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class ApplicationActionUtils {
  private ApplicationActionUtils() {
  }

  @Nullable
  public static DeploymentNode getDeploymentTarget(@NotNull AnActionEvent e) {
    return ServiceViewActionUtils.getTarget(e, DeploymentNode.class);
  }

  @Nullable
  public static Deployment getDeployment(@Nullable DeploymentNode node) {
    return node == null ? null : ObjectUtils.tryCast(node.getValue(), Deployment.class);
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <T> T getApplicationRuntime(@Nullable DeploymentNode node, @NotNull Class<T> clazz) {
    Deployment deployment = getDeployment(node);
    return deployment == null ? null : ObjectUtils.tryCast(deployment.getRuntime(), clazz);
  }

  @Nullable
  public static <T> T getApplicationRuntime(@NotNull AnActionEvent e, @NotNull Class<T> clazz) {
    Deployment deployment = getDeployment(getDeploymentTarget(e));
    return deployment == null ? null : ObjectUtils.tryCast(deployment.getRuntime(), clazz);
  }

  @NotNull
  public static Runnable createLogSelector(@NotNull Project project,
                                           @NotNull ServersTreeNodeSelector selector,
                                           @NotNull DeploymentNode node,
                                           @NotNull String logName) {
    SelectLogRunnable selectLogRunnable = new SelectLogRunnable(project, selector, node, logName);
    DisposableSelectLogRunnableWrapper wrapper = new DisposableSelectLogRunnableWrapper(selectLogRunnable);
    Disposer.register(project, wrapper);
    return wrapper;
  }

  private static final class DisposableSelectLogRunnableWrapper implements Runnable, Disposable {
    private volatile Runnable mySelectLogRunnable;

    private DisposableSelectLogRunnableWrapper(Runnable selectLogRunnable) {
      mySelectLogRunnable = selectLogRunnable;
    }

    @Override
    public void dispose() {
      mySelectLogRunnable = null;
    }

    @Override
    public void run() {
      Runnable selectLogRunnable = mySelectLogRunnable;
      if (selectLogRunnable != null) {
        selectLogRunnable.run();
        Disposer.dispose(this);
      }
    }
  }

  private static class SelectLogRunnable implements Runnable {
    private final Project myProject;
    private final ServersTreeNodeSelector mySelector;
    private final DeploymentNode myNode;
    private final String myLogName;

    SelectLogRunnable(@NotNull Project project,
                      @NotNull ServersTreeNodeSelector selector,
                      @NotNull DeploymentNode node,
                      @NotNull String logName) {
      myProject = project;
      mySelector = selector;
      myNode = node;
      myLogName = logName;
    }

    @Override
    public void run() {
      RemoteServer<?> server = (RemoteServer<?>)myNode.getServerNode().getValue();
      ServerConnection<?> connection = ServerConnectionManager.getInstance().getConnection(server);
      if (connection == null) return;

      Deployment deployment = findDeployment(connection);
      if (deployment == null) return;

      AppUIExecutor.onUiThread().expireWith(myProject).submit(() -> mySelector.select(connection, deployment.getName(), myLogName));
    }

    private Deployment findDeployment(ServerConnection<?> connection) {
      DeploymentRuntime applicationRuntime = getApplicationRuntime(myNode, DeploymentRuntime.class);
      for (Deployment deployment : connection.getDeployments()) {
        if (applicationRuntime == deployment.getRuntime()) {
          return deployment;
        }
      }
      return null;
    }
  }
}
