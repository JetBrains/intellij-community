package com.intellij.execution.multilaunch.state

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.XMap

class ConditionSnapshot : BaseState() {
  var type by string()
  @get:XMap
  val attributes by map<String, String>()
}
