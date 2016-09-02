/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.ide

import com.intellij.execution.ExecutionManager
import com.intellij.execution.console.*
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
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
  val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false)
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
      "'application' and 'project' variables are available at the top level. \n",
      ConsoleViewContentType.SYSTEM_OUTPUT
  )
  console.print(
      "Default imports:\n${defaultImports.map { "\t$it" }.joinToString(separator = ",\n")}\n\n",
      ConsoleViewContentType.SYSTEM_OUTPUT
  )
}

private fun createExecuteHandler(project: Project) = object : BaseConsoleExecuteActionHandler(false) {

  val interpreter: Interpreter by lazy {
    val binding = Binding(mapOf(
        "application" to ApplicationManager.getApplication(),
        "project" to project
    ))
    Interpreter(Interpreter::class.java.classLoader, binding)
  }

  override fun execute(text: String, console: LanguageConsoleView) {
    ApplicationManager.getApplication().executeOnPooledThread {
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
