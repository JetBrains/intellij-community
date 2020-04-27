package com.intellij.remoteServer.impl.runtime.ui.tree;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public interface ServerNode extends ServersTreeNode {
  @Nullable
  Project getProject();
}
