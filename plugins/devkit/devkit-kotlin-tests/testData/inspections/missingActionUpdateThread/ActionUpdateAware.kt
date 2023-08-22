package com.sample

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionUpdateThread.*

// plain
open class <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">AwareError</warning> : ActionUpdateThreadAware {
}

open class AwareNoError : ActionUpdateThreadAware {
  override fun getActionUpdateThread(): ActionUpdateThread = EDT
}

// inherited
class <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">AwareChildError</warning> : AwareError()
class AwareChildNoError : AwareNoError()

// anonymous
class Holder {
  val awareError = <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">object</warning>: AwareError() {
  }

  val awareNoError = object : AwareError() {
    override fun getActionUpdateThread(): ActionUpdateThread = EDT
  };
}
