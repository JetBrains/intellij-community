package com.intellij.execution.multilaunch.design

import com.intellij.execution.multilaunch.execution.conditions.Condition
import com.intellij.execution.multilaunch.execution.executables.Executable

data class ExecutableRow(
  var executable: Executable?,
  var condition: Condition?,
  var disableDebugging: Boolean
)

