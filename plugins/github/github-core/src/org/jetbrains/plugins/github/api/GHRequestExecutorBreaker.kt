// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

@Service
class GHRequestExecutorBreaker {

  @Volatile
  var isRequestsShouldFail = false

  class Action : ToggleAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent) =
      service<GHRequestExecutorBreaker>().isRequestsShouldFail


    override fun setSelected(e: AnActionEvent, state: Boolean) {
      service<GHRequestExecutorBreaker>().isRequestsShouldFail = state
    }
  }
}