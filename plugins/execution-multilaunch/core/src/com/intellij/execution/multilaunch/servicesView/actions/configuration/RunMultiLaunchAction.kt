package com.intellij.execution.multilaunch.servicesView.actions.configuration

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.icons.AllIcons
import com.intellij.execution.multilaunch.MultiLaunchBundle

@Suppress("ComponentNotRegistered")
class RunMultiLaunchAction(settings: RunnerAndConfigurationSettings) : ExecuteMultiLaunchAction(settings, MultiLaunchBundle.message("action.multilaunch.RunMultiLaunchAction.text"), AllIcons.Actions.Execute, ExecutionMode.Run)