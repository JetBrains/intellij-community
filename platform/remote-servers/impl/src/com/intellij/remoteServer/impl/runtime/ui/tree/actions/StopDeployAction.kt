// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.ui.tree.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.remoteServer.eventbus.EventBus
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus.DEPLOYING
import com.intellij.remoteServer.util.ApplicationActionUtils

class StopDeployAction : DeployAction() {

  override fun update(e: AnActionEvent) {
    val node = ApplicationActionUtils.getDeploymentTarget(e)
    val presentation = e.presentation

    if (node is ServersTreeStructure.DeploymentNodeImpl) {
      val visible = node.isDeployActionVisible && !node.isDeployed

      presentation.isVisible = visible
      presentation.isEnabled = visible && node.isDeployActionEnabled && node.deployment.status == DEPLOYING
    }
    else presentation.isVisible = false
  }

  override fun actionPerformed(e: AnActionEvent) {
    ApplicationActionUtils.getDeploymentTarget(e)?.apply { EventBus.emmitAndRemove("$deploymentName:stop") }
  }
}