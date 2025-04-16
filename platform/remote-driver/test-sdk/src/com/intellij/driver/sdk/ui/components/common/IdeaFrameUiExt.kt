package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.elements.fileChooser
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

private const val TOOL_WINDOW_ROOT_COMPONENT_CLASS = "com.intellij.toolWindow.InternalDecoratorImpl"

fun IdeaFrameUI.buildToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = x { byType("com.intellij.build.BuildView") }.apply(action)

fun IdeaFrameUI.runToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = toolWindow("Main", action)

fun IdeaFrameUI.notificationsToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = toolWindow("Notifications", action)

fun IdeaFrameUI.structureToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = toolWindow("Structure", action)

fun IdeaFrameUI.commitToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = toolWindow("Commit", action)

fun IdeaFrameUI.databaseToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = toolWindow("Database", action)

fun IdeaFrameUI.mesonToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = x { byClass("MesonToolWindowPanel") }.apply(action)

fun IdeaFrameUI.messagesToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = toolWindow("Build", action)

fun IdeaFrameUI.terminalToolWindow(action: UiComponent.() -> Unit = {}): UiComponent =
  x { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byType("org.jetbrains.plugins.terminal.TerminalToolWindowPanel")) }.apply(action)

fun IdeaFrameUI.problemsToolWindow(action: UiComponent.() -> Unit = {}): UiComponent =
  x { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byAccessibleName("Problems")) }.apply(action)

fun IdeaFrameUI.jupyterToolWindow(action: UiComponent.() -> Unit = {}): UiComponent =
  x { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byAccessibleName("Jupyter")) }.apply(action)

fun IdeaFrameUI.vcsToolWindow(action: UiComponent.() -> Unit = {}): UiComponent =
  x("//div[contains(@class, 'InternalDecorator') and (contains(@accessiblename, 'Branches') " +
    "or contains(@accessiblename, 'Log') or contains(@accessiblename, 'Version Control') " +
    "or contains(@accessiblename, 'History') or contains(@accessiblename, 'Console'))]").apply(action)

fun IdeaFrameUI.todoToolWindow(action: UiComponent.() -> Unit = {}): UiComponent =
  x { componentWithChild(byType(TOOL_WINDOW_ROOT_COMPONENT_CLASS), byAccessibleName("TODO")) }.apply(action)

fun IdeaFrameUI.toolWindow(name: String, action: UiComponent.() -> Unit = {}) = x { byAccessibleName("$name Tool Window") }.apply(action)

fun IdeaFrameUI.invokeOpenFileAction(file: Path) {
  driver.invokeAction("OpenFile", now = false)
  fileChooser({ byTitle("Open File or Project") }).waitFound(10.seconds).openPath(file)
}

fun IdeaFrameUI.requireProject() = checkNotNull(project) { "no project" }
