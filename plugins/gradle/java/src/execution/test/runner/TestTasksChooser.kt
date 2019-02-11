// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer.findAllTestsTaskToRun
import org.jetbrains.plugins.gradle.util.TasksToRun
import java.util.function.Consumer
import javax.swing.DefaultListCellRenderer

typealias SourcePath = String
typealias TestName = String
typealias TestTasks = List<String>

open class TestTasksChooser {
  private val LOG = Logger.getInstance(TestTasksChooser::class.java)

  @Suppress("CAST_NEVER_SUCCEEDS")
  private fun error(message: String): Nothing = LOG.error(message) as Nothing

  fun chooseTestTasks(
    context: ConfigurationContext,
    elements: Iterable<PsiElement>,
    perform: Consumer<List<Map<SourcePath, TestTasks>>>
  ) {
    val sources = elements.map { getSourceFile(it) ?: error("Can not find source file for $it") }
    chooseTestTasks(context.dataContext, sources, context.project, perform)
  }

  fun chooseTestTasks(
    context: ConfigurationContext,
    vararg elements: PsiElement,
    perform: Consumer<List<Map<SourcePath, TestTasks>>>
  ) {
    chooseTestTasks(context, elements.asIterable(), perform)
  }

  private fun chooseTestTasks(
    context: DataContext,
    sources: List<VirtualFile>,
    project: Project,
    perform: Consumer<List<Map<SourcePath, TestTasks>>>
  ) {
    val testTasks = findAllTestsTaskToRun(sources, project)
    when {
      testTasks.isEmpty() -> showWarningTooltip(context)
      testTasks.size == 1 -> perform.accept(testTasks.values.toList())
      else -> chooseTestTasks(context, testTasks, perform)
    }
  }

  private fun findAllTestsTaskToRun(
    sources: List<VirtualFile>,
    project: Project
  ): Map<TestName, Map<SourcePath, TasksToRun>> {
    val testTasks: Map<SourcePath, Map<TestName, TasksToRun>> =
      sources.map { source -> source.path to findAllTestsTaskToRun(source, project).map { it.testName to it }.toMap() }.toMap()
    val testTaskNames = testTasks.flatMap { it.value.keys }.toSet()
    return testTaskNames.map { name -> name to testTasks.mapNotNullValues { it.value[name] } }.toMap()
  }

  protected open fun chooseTestTasks(
    context: DataContext,
    testTasks: Map<TestName, Map<SourcePath, TasksToRun>>,
    perform: Consumer<List<Map<SourcePath, TestTasks>>>
  ) {
    assert(!ApplicationManager.getApplication().isCommandLine)
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(testTasks.keys.toList())
      .setRenderer(DefaultListCellRenderer())
      .setTitle("Choose Tasks to Run")
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemsChosenCallback {
        val choosesTestTasks = it.mapNotNull(testTasks::get)
        when {
          choosesTestTasks.isEmpty() -> showWarningTooltip(context)
          else -> perform.accept(choosesTestTasks)
        }
      }
      .createPopup()
      .showInBestPositionFor(context)
  }

  protected open fun showWarningTooltip(context: DataContext) {
    assert(!ApplicationManager.getApplication().isCommandLine)
    JBPopupFactory.getInstance()
      .createMessage("No tasks available")
      .showInBestPositionFor(context)
  }

  companion object {
    private fun <K, V, R> Map<K, V>.mapNotNullValues(transform: (Map.Entry<K, V>) -> R?): Map<K, R> =
      mapNotNull { entry -> transform(entry)?.let { entry.key to it } }.toMap()
  }
}