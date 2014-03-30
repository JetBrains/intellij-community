package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.impl.runtime.ui.tree.TreeBuilderBase;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This is a temporary solution to integrate JavaEE based application servers into common Remote Servers/Clouds view. It should be removed
 * when remote app servers will be migrated to use remote-servers-api
 *
 * @author nik
 */
public abstract class RemoteServersViewContributor {
  public static final ExtensionPointName<RemoteServersViewContributor> EP_NAME = ExtensionPointName.create("com.intellij.remoteServer.viewContributor");

  public abstract boolean canContribute(@NotNull Project project);

  public abstract void setupAvailabilityListener(@NotNull Project project, @NotNull Runnable checkAvailability);

  public abstract void setupTree(Project project, Tree tree, TreeBuilderBase builder);

  @NotNull
  public abstract List<AbstractTreeNode<?>> createServerNodes(Project project);

  @Nullable
  public abstract Object getData(@NotNull String dataId, @NotNull ServersToolWindowContent content);
}
