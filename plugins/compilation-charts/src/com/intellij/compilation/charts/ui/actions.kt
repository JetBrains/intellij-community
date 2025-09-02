// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.compilation.charts.CompilationChartsBundle
import com.intellij.compilation.charts.impl.ModuleKey
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionButton
import java.nio.file.Paths
import javax.swing.JComponent

interface CompilationChartsAction {
  fun isAccessible(): Boolean
  fun position(): Position
  fun draw(row: Row)

  enum class Position {
    LEFT,
    RIGHT,
    LIST
  }
}

class OpenDirectoryAction(private val project: Project, private val key: ModuleKey, private val close: () -> Unit) : CompilationChartsAction {
  override fun isAccessible(): Boolean = true
  override fun position(): CompilationChartsAction.Position = CompilationChartsAction.Position.LEFT
  override fun draw(row: Row) {
    val text = CompilationChartsBundle.message("charts.action.open.module.directory.text")
    val description = CompilationChartsBundle.message("charts.action.open.module.directory.description")
    val action = object : DumbAwareAction(text, description, Settings.Popup.rootIcon(key.test)) {
      override fun actionPerformed(e: AnActionEvent) {
        val module = ModuleManager.getInstance(project).findModuleByName(key.name) ?: return
        val path = findModuleDirectory(module.moduleFilePath) ?: return
        close()
        OpenFileDescriptor(project, path, -1).navigate(true)
      }
    }
    row.actionButton(action).align(AlignX.LEFT)
  }

  private fun findModuleDirectory(path: String): VirtualFile? = LocalFileSystem.getInstance().let { fs ->
    val file = fs.findFileByPath(path)
    if (file != null) return file.parent
    val parent = Paths.get(path).parent ?: return null
    return fs.findFileByPath(parent.toString())
  }
}

class OpenProjectStructureAction(
  private val project: Project,
  private val name: String,
  private val close: () -> Unit,
) : CompilationChartsAction {
  override fun isAccessible(): Boolean = true
  override fun position(): CompilationChartsAction.Position = CompilationChartsAction.Position.RIGHT
  override fun draw(row: Row) {
    val text = CompilationChartsBundle.message("charts.action.open.project.structure.text")
    val description = CompilationChartsBundle.message("charts.action.open.project.structure.description")
    val action = object : DumbAwareAction(text, description, Settings.Popup.EDIT_ICON) {
      override fun actionPerformed(e: AnActionEvent) {
        val module = ModuleManager.getInstance(project).findModuleByName(name) ?: return
        val projectStructure = ProjectStructureConfigurable.getInstance(project)
        ShowSettingsUtil.getInstance().editConfigurable(project, projectStructure) {
          close()
          projectStructure.select(module.name, "Modules", true)
        }
      }
    }
    row.actionButton(action).align(AlignX.RIGHT)
  }
}

class ShowModuleDependenciesAction(
  private val project: Project, private val name: String,
  private val component: JComponent,
  private val close: () -> Unit,
) : CompilationChartsAction {
  private val action = ActionManager.getInstance().getAction("ShowModulesDependencies")

  override fun isAccessible(): Boolean = action != null

  override fun position(): CompilationChartsAction.Position = CompilationChartsAction.Position.LIST

  @Suppress("DialogTitleCapitalization", "UNCHECKED_CAST")
  override fun draw(row: Row) {
    row.link(action?.templateText ?: "") {
      val module = ModuleManager.getInstance(project).findModuleByName(name)
      val context =  SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, project)
          .add(LangDataKeys.MODULE_CONTEXT_ARRAY, notNullArrayOf(module))
          .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, component)
          .build()
      close()
      action.actionPerformed(AnActionEvent.createEvent(action, context, null, ActionPlaces.POPUP, ActionUiKind.TOOLBAR, null))
    }
  }
}

class ShowMatrixDependenciesAction(
  private val project: Project, private val name: String,
  private val component: JComponent,
  private val close: () -> Unit,
) : CompilationChartsAction {
  private val action = ActionManager.getInstance().getAction("DSM.Analyze")

  override fun isAccessible(): Boolean = action != null

  override fun position(): CompilationChartsAction.Position = CompilationChartsAction.Position.LIST

  @Suppress("DialogTitleCapitalization", "UNCHECKED_CAST")
  override fun draw(row: Row) {
    row.link(action?.templateText ?: "") {
      val module = ModuleManager.getInstance(project).findModuleByName(name)
      val context =  SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(LangDataKeys.MODULE, module)
        .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, component)
        .build()
      close()
      action.actionPerformed(AnActionEvent.createEvent(action, context, null, ActionPlaces.POPUP, ActionUiKind.TOOLBAR, null))
    }
  }
}

private inline fun <reified T> notNullArrayOf(vararg elements: T): Array<T> {
  val size = elements.count { it != null }
  val array = arrayOfNulls<T>(size)
  var index = 0
  for (element in elements) {
    if (element != null) array[index++] = element
  }
  @Suppress("UNCHECKED_CAST")
  return array as Array<T>
}