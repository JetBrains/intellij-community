package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServerNode;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class EditServerConfigAction extends DebugServerAction.SingleServerActionBase {
  public EditServerConfigAction() {
    super("Edit Configuration", "Edit configuration of the selected server",
          AllIcons.Actions.EditSource);
  }

  @Override
  protected void doActionPerformed(@NotNull ServersToolWindowContent content, @NotNull ServerNode server) {
    server.editConfiguration();
  }

  protected boolean isEnabledForServer(ServerNode serverNode) {
    return true;
  }
}
