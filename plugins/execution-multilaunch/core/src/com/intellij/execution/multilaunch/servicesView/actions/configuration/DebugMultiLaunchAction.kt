package com.intellij.execution.multilaunch.servicesView.actions.configuration

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.icons.AllIcons
import com.intellij.execution.multilaunch.MultiLaunchBundle

class DebugMultiLaunchAction(settings: RunnerAndConfigurationSettings) : ExecuteMultiLaunchAction(settings, MultiLaunchBundle.message("action.multilaunch.DebugMultiLaunchAction.text"), AllIcons.Actions.StartDebugger, ExecutionMode.Debug)