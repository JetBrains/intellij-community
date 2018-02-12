/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.ide

import com.intellij.execution.ExecutionManager
import com.intellij.execution.console.*
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import groovy.lang.Binding
import groovy.lang.GroovySystem
import org.codehaus.groovy.tools.shell.Interpreter
import org.jetbrains.plugins.groovy.GroovyLanguage
import java.awt.BorderLayout
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JPanel

class GroovyScriptingShellAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent?) {
    val project = e?.project ?: return
    initConsole(project)
  }

  override fun update(e: AnActionEvent?) {
    e?.presentation?.isEnabledAndVisible = ApplicationManager.getApplication().isInternal
  }
}

object MyConsoleRootType : ConsoleRootType("groovy.scripting.shell", null)

private val TITLE = "Groovy IDE Scripting Shell"

private val defaultImports = listOf(
  "com.intellij.openapi.application.*",
  "com.intellij.openapi.project.*",
  "com.intellij.openapi.module.*",
  "com.intellij.openapi.vfs.*",
  "com.intellij.psi.*",
  "com.intellij.psi.search.*",
  "com.intellij.psi.stubs.*",
  "com.intellij.util.indexing.*"
)

private val defaultImportStatements = defaultImports.map {
  "import $it"
}

private fun initConsole(project: Project) {
  val console = LanguageConsoleImpl(project, TITLE, GroovyLanguage)
  val action = ConsoleExecuteAction(console, createExecuteHandler(project))
  action.registerCustomShortcutSet(action.shortcutSet, console.consoleEditor.component)
  ConsoleHistoryController(MyConsoleRootType, null, console).install()

  val consoleComponent = console.component

  val toolbarActions = DefaultActionGroup()
  val toolbar = ActionManager.getInstance().createActionToolbar("GroovyScriptingConsole", toolbarActions, false)
  toolbar.setTargetComponent(consoleComponent)

  val panel = JPanel(BorderLayout())
  panel.add(consoleComponent, BorderLayout.CENTER)
  panel.add(toolbar.component, BorderLayout.WEST)

  val descriptor = object : RunContentDescriptor(console, null, panel, TITLE) {
    override fun isContentReuseProhibited() = true
  }
  toolbarActions.addAll(*console.createConsoleActions())
  val executor = DefaultRunExecutor.getRunExecutorInstance()
  toolbarActions.add(CloseAction(executor, descriptor, project))
  ExecutionManager.getInstance(project).contentManager.showRunContent(executor, descriptor)

  val appInfo = ApplicationInfo.getInstance()
  val namesInfo = ApplicationNamesInfo.getInstance()
  val buildDate = SimpleDateFormat("dd MMM yy HH:ss", Locale.US).format(appInfo.buildDate.time)
  console.print(
    "Welcome!\n${namesInfo.fullProductName} (build #${appInfo.build}, $buildDate); Groovy: ${GroovySystem.getVersion()}\n",
    ConsoleViewContentType.SYSTEM_OUTPUT
  )
  console.print(
    "'application', 'project', 'facade' and 'allScope' variables are available at the top level. \n",
    ConsoleViewContentType.SYSTEM_OUTPUT
  )
  console.print(
    "Default imports:\n${defaultImports.joinToString(separator = ",\n") { "\t$it" }}\n\n",
    ConsoleViewContentType.SYSTEM_OUTPUT
  )
}

private fun createExecuteHandler(project: Project) = object : BaseConsoleExecuteActionHandler(false) {

  val interpreter: Interpreter by lazy {
    val binding = Binding(mapOf(
      "application" to ApplicationManager.getApplication(),
      "project" to project,
      "facade" to JavaPsiFacade.getInstance(project),
      "allScope" to GlobalSearchScope.allScope(project)
    ))
    Interpreter(Interpreter::class.java.classLoader, binding)
  }

  override fun execute(text: String, console: LanguageConsoleView) {
    ApplicationManager.getApplication().executeOnPooledThread {
      runReadAction {
        try {
          val result: Any? = interpreter.evaluate(defaultImportStatements + "" + "" + text)
          console.print("Returned: ", ConsoleViewContentType.SYSTEM_OUTPUT)
          console.print("$result\n", ConsoleViewContentType.NORMAL_OUTPUT)
        }
        catch (e: Throwable) {
          val errors = StringWriter()
          e.printStackTrace(PrintWriter(errors))
          console.print("Error: $errors", ConsoleViewContentType.ERROR_OUTPUT)
        }
      }
    }
  }
}
