// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer.findAllTestsTaskToRun
import org.jetbrains.plugins.gradle.util.TasksToRun
import java.util.*
import javax.swing.DefaultListCellRenderer

abstract class TasksChooser {
  private val LOG = Logger.getInstance(TasksChooser::class.java)

  @Suppress("CAST_NEVER_SUCCEEDS")
  private fun error(message: String): Nothing = LOG.error(message) as Nothing

  protected abstract fun choosesTasks(tasks: List<Map<String, List<String>>>)

  fun runTaskChoosing(context: ConfigurationContext, vararg elements: PsiElement) {
    val sources = elements.map { it.containingFile?.virtualFile ?: error("Can not find source file for $it") }
    runTaskChoosing(context.dataContext, sources, context.project)
  }

  private fun runTaskChoosing(context: DataContext, sources: List<VirtualFile>, project: Project) {
    val tasks = findAllTestsTaskToRun(sources, project)
    if (tasks.isEmpty()) return
    if (tasks.size == 1) {
      choosesTasks(tasks)
      return
    }
    val identifiedTasks = tasks.map { createIdentifier(it) to it }.toMap()
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(identifiedTasks.keys.toList())
      .setRenderer(DefaultListCellRenderer())
      .setTitle("Choose tasks to run...")
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemsChosenCallback { chooses ->
        chooseAndPerform(chooses.mapNotNull(identifiedTasks::get))
      }
      .createPopup()
      .showInBestPositionFor(context)
  }

  private fun findAllTestsTaskToRun(sources: List<VirtualFile>, project: Project): List<Map<String, TasksToRun>> {
    val tasks = sources.map { source -> source.path to findAllTestsTaskToRun(source, project).map { it.testName to it }.toMap() }
    val taskNames = tasks.map { it.second.keys }.reduce { acc, it -> acc intersect it }
    return taskNames.map { name -> tasks.map { it.first to it.second.getValue(name) }.toMap() }
  }

  private fun chooseAndPerform(tasks: List<Map<String, List<String>>>) {
    when {
      tasks.isEmpty() -> return
      else -> choosesTasks(tasks)
    }
  }

  private fun createIdentifier(tasks: Map<String, TasksToRun>): String {
    assert(tasks.isNotEmpty())
    val joiner = StringJoiner(" ")
    joiner.add(tasks.values.first().testName)
    tasks.values.map { it.module.name }.toSet()
      .fold(joiner, StringJoiner::add)
    return joiner.toString()
  }
}