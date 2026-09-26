package com.intellij.execution.multilaunch.state

import com.intellij.openapi.components.BaseState

class ExecutableSnapshot : BaseState() {
  var id by string()
  val attributes by map<String, String>()
}
