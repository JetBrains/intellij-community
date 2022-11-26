package com.sample

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionUpdateThread.*

// plain
open class <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">ActionError</warning> : AnAction() {
  override fun update(e: AnActionEvent) { }
}

open class ActionNoError : AnAction() {
  override fun update(e: AnActionEvent) { }
  override fun getActionUpdateThread(): ActionUpdateThread = EDT
}

// inherited
class <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">ActionChildError</warning> : ActionError()
class ActionChildNoError : ActionNoError()

// anonymous
class Holder {
  val actionError: AnAction = <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">object</warning>: ActionError() {
  }

  val actionNoError: AnAction = object : ActionError() {
    override fun getActionUpdateThread(): ActionUpdateThread = EDT
  }
}
