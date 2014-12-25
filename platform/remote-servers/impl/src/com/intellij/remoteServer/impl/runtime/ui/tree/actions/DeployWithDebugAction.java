/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;

public class DeployWithDebugAction extends ServersTreeAction<DeploymentNode> {

  public DeployWithDebugAction() {
    super("Debug", "Deploy and debug the selected item", AllIcons.Actions.StartDebugger);
  }

  @Override
  protected Class<DeploymentNode> getTargetNodeClass() {
    return DeploymentNode.class;
  }

  @Override
  protected boolean isVisible4(DeploymentNode node) {
    return node.isDeployActionVisible() && node.isDebugActionVisible();
  }

  @Override
  protected boolean isEnabled4(DeploymentNode node) {
    return node.isDeployActionEnabled();
  }

  @Override
  protected void doActionPerformed(DeploymentNode node) {
    node.deployWithDebug();
  }
}
