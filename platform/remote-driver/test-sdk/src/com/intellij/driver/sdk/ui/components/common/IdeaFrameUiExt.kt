package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent
import com.intellij.driver.sdk.ui.components.elements.fileChooser
import java.nio.file.Path

private const val TOOL_WINDOW_ROOT_COMPONENT_CLASS = "com.intellij.toolWindow.InternalDecoratorImpl"

fun IdeaFrameUI.buildToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byType("com.intellij.build.BuildView")) }.apply(action)

fun IdeaFrameUI.runToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byType("com.intellij.execution.impl.ConsoleViewImpl")) }.apply(action)

fun IdeaFrameUI.notificationsToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent = toolWindow("Notifications", action)

fun IdeaFrameUI.structureToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent = toolWindow("Structure", action)

fun IdeaFrameUI.commitToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent = toolWindow("Commit", action)

fun IdeaFrameUI.databaseToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent = toolWindow("Database", action)

fun IdeaFrameUI.mesonToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byClass("MesonToolWindowPanel")) }.apply(action)

fun IdeaFrameUI.messagesToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent = toolWindow("Build", action)

fun IdeaFrameUI.terminalToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byType("org.jetbrains.plugins.terminal.TerminalToolWindowPanel")) }.apply(action)

fun IdeaFrameUI.problemsToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byAccessibleName("Problems")) }.apply(action)

fun IdeaFrameUI.jupyterToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byAccessibleName("Jupyter")) }.apply(action)

fun IdeaFrameUI.vcsToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) {
    and(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS),
        or(contains(byAccessibleName("Branches")),
          contains(byAccessibleName("Log")),
          contains(byAccessibleName("Version Control")),
          contains(byAccessibleName("History")),
          contains(byAccessibleName("Console"))
        )
    )
  }.apply(action)

fun IdeaFrameUI.todoToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byAccessibleName("TODO")) }.apply(action)

fun IdeaFrameUI.toolWindow(name: String, action: ToolWindowUiComponent.() -> Unit = {}) = x(ToolWindowUiComponent::class.java) { byAccessibleName("$name Tool Window") }.apply(action)

fun IdeaFrameUI.invokeOpenFileAction(file: Path) {
  driver.invokeAction("OpenFile", now = false)
  fileChooser({ byTitle("Open File or Project") }).openPath(file)
}

fun IdeaFrameUI.requireProject() = checkNotNull(project) { "no project" }
