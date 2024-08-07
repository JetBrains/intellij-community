// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.scope.TestsScope
import com.intellij.ui.FileColorManager
import com.intellij.util.FunctionUtil
import com.intellij.util.getBestBalloonPosition
import com.intellij.util.getBestPopupPosition
import com.intellij.util.ui.JBUI
import icons.ExternalSystemIcons
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer.findAllTestsTaskToRun
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.TasksToRun
import java.awt.Component
import java.util.function.Consumer
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.border.EmptyBorder

typealias SourcePath = String
typealias TestName = String

open class TestTasksChooser {
  private fun error(message: String): Nothing {
    LOG.error(message)
    throw IllegalArgumentException(message)
  }

  fun chooseTestTasks(
    project: Project,
    context: DataContext,
    elements: Iterable<PsiElement>,
    consumer: Consumer<List<Map<SourcePath, TasksToRun>>>
  ) {
    val sources = elements.map { getSourceFile(it) ?: error("Can not find source file for $it") }
    val testTasks = findAllTestsTaskToRun(sources, project)
    chooseTestTasks(project, context, testTasks, consumer::accept)
  }

  open fun <T> chooseTestTasks(
    project: Project,
    context: DataContext,
    testTasks: Map<TestName, T>,
    consumer: (List<T>) -> Unit
  ) {
    when {
      testTasks.isEmpty() -> showTestTasksNotFoundWarning(project, context)
      testTasks.size == 1 -> consumer(testTasks.values.toList())
      else -> showTestTasksPopupChooser(project, context, testTasks, consumer)
    }
  }

  private fun findAllTestsTaskToRun(
    sources: List<VirtualFile>,
    project: Project
  ): Map<TestName, Map<SourcePath, TasksToRun>> {
    val testTasks: Map<SourcePath, Map<TestName, TasksToRun>> =
      sources.associate { source -> source.path to findAllTestsTaskToRun(source, project).associateBy { it.testName } }
    val testTaskNames = testTasks.flatMap { it.value.keys }.toSet()
    return testTaskNames.associateWith { name -> testTasks.mapNotNullValues { it.value[name] } }
  }

  protected open fun <T> showTestTasksPopupChooser(
    project: Project,
    context: DataContext,
    testTasks: Map<TestName, T>,
    consumer: (List<T>) -> Unit
  ) {
    assert(!ApplicationManager.getApplication().isCommandLine)
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(
        testTasks.keys.toList()
          .sortedByDescending { it == TEST_TASK_NAME }
      )
      .setRenderer(TestTaskListCellRenderer(project))
      .setTitle(suggestPopupTitle(context))
      .setAutoselectOnMouseMove(false)
      .setNamerForFiltering(FunctionUtil.id())
      .setMovable(true)
      .setAdText(GradleBundle.message("gradle.tests.tasks.choosing.popup.hint"))
      .setResizable(false)
      .setRequestFocus(true)
      .setMinSize(JBUI.size(270, 55))
      .setItemsChosenCallback {
        val choosesTestTasks = it.mapNotNull(testTasks::get)
        when {
          choosesTestTasks.isEmpty() -> showTestTasksNotFoundWarning(project, context)
          else -> consumer(choosesTestTasks)
        }
      }
      .createPopup()
      .show(getBestPopupPosition(context))
  }

  protected open fun showTestTasksNotFoundWarning(project: Project, context: DataContext) {
    assert(!ApplicationManager.getApplication().isCommandLine)
    JBPopupFactory.getInstance()
      .createBalloonBuilder(JLabel(GradleBundle.message("gradle.tests.tasks.choosing.warning.text")))
      .setFillColor(IdeTooltipManager.getInstance().getTextBackground(false))
      .createBalloon()
      .show(getBestBalloonPosition(context), Balloon.Position.above)
  }

  @NlsContexts.PopupTitle
  private fun suggestPopupTitle(context: DataContext): String {
    return when (val locationName = context.getData(LOCATION)) {
      null -> GradleBundle.message("gradle.tests.tasks.choosing.popup.title.common")
      else -> GradleBundle.message("gradle.tests.tasks.choosing.popup.title", locationName)
    }
  }

  private class TestTaskListCellRenderer(project: Project) : DefaultListCellRenderer() {
    private val cellInsets = JBUI.insets(1, 5)
    private val colorManager = FileColorManager.getInstance(project)

    override fun getListCellRendererComponent(
      list: JList<*>?,
      value: Any?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ): Component {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      text = value.toString()
      icon = ExternalSystemIcons.Task
      iconTextGap = cellInsets.left
      border = EmptyBorder(cellInsets)
      if (!isSelected) {
        background = colorManager.getScopeColor(TestsScope.NAME)
      }
      return this
    }
  }

  companion object {
    private val LOG = Logger.getInstance(TestTasksChooser::class.java)

    private const val TEST_TASK_NAME = "test"

    @JvmField
    val LOCATION = DataKey.create<String>("org.jetbrains.plugins.gradle.execution.test.runner.TestTasksChooser.LOCATION")

    @JvmStatic
    fun contextWithLocationName(context: DataContext, locationName: String?): DataContext {
      if (locationName == null) return context
      return CustomizedDataContext.withSnapshot(context) { sink ->
        sink[LOCATION] = locationName
      }
    }

    private fun <K, V, R> Map<K, V>.mapNotNullValues(transform: (Map.Entry<K, V>) -> R?): Map<K, R> =
      mapNotNull { entry -> transform(entry)?.let { entry.key to it } }.toMap()
  }
}