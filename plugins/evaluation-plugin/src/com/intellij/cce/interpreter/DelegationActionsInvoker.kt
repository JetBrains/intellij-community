package com.intellij.cce.interpreter

import com.intellij.cce.actions.TextRange
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.util.ListSizeRestriction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project

class DelegationActionsInvoker(private val invoker: ActionsInvoker, private val project: Project) : ActionsInvoker {
  private val applicationListenersRestriction = ListSizeRestriction.applicationListeners()
  private val dumbService = DumbService.getInstance(project)

  override fun moveCaret(offset: Int) = onEdt {
    invoker.moveCaret(offset)
  }

  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session {
    return readActionWaitingForSize {
      invoker.callFeature(expectedText, offset, properties)
    }
  }

  override fun rename(text: String) {
    return WriteCommandAction.runWriteCommandAction(project) {
      invoker.rename(text)
    }
  }

  override fun printText(text: String) = writeAction {
    invoker.printText(text)
  }

  override fun deleteRange(begin: Int, end: Int) = writeAction {
    invoker.deleteRange(begin, end)
  }

  override fun openFile(file: String): String = readAction {
    invoker.openFile(file)
  }

  override fun closeFile(file: String) = onEdt {
    invoker.closeFile(file)
  }

  override fun isOpen(file: String) = readAction {
    invoker.isOpen(file)
  }

  override fun save() = writeAction {
    invoker.save()
  }

  override fun getText(): String = readAction {
    invoker.getText()
  }

  private fun <T> readActionWaitingForSize(size: Int = 100, action: () -> T): T {
    applicationListenersRestriction.waitForSize(size)
    return readAction(action)
  }

  private fun <T> readAction(runnable: () -> T): T {
    var result: T? = null
    ApplicationManager.getApplication().invokeAndWait {
      result = dumbService.runReadActionInSmartMode<T>(runnable)
    }

    return result!!
  }

  private fun writeAction(action: () -> Unit) {
    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction(action)
    }
  }

  private fun onEdt(action: () -> Unit) = ApplicationManager.getApplication().invokeAndWait {
    action()
  }
}
