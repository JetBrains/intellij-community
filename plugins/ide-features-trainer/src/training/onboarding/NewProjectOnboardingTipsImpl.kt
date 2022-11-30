// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.onboarding

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.wizard.NewProjectOnboardingTips
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import org.jetbrains.annotations.NonNls
import training.learn.LearnBundle
import training.ui.LearningUiUtil

private var Project.onboardingTipsDebugPath: String?
  get() = PropertiesComponent.getInstance(this).getValue("onboarding.tips.debug.path")
  set(value) { PropertiesComponent.getInstance(this).setValue("onboarding.tips.debug.path", value) }

private class NewProjectOnboardingTipsImpl : NewProjectOnboardingTips {
  @RequiresEdt
  override fun installTips(project: Project) {
    val fileEditorManager = FileEditorManager.getInstance(project)

    val textEditor = fileEditorManager.selectedEditor as? TextEditor ?: return

    val document = textEditor.editor.document
    // need to generalize this code in the future
    val offset = document.charsSequence.indexOf("System.out.println").takeIf { it >= 0 } ?: return

    val file = textEditor.file
    val position = XDebuggerUtil.getInstance().createPositionByOffset(file, offset) ?: return

    XBreakpointUtil.toggleLineBreakpoint(project, position, textEditor.editor, false, false, true)

    val pathToRunningFile = file.path
    project.onboardingTipsDebugPath = pathToRunningFile
    installDebugListener(project, pathToRunningFile)
  }
}

private class InstallOnboardingTooltip : StartupActivity {
  override fun runActivity(project: Project) {
    val pathToRunningFile = project.onboardingTipsDebugPath
    if (pathToRunningFile != null) {
      installDebugListener(project, pathToRunningFile)
    }
  }
}

private fun installDebugListener(project: Project, pathToRunningFile: @NonNls String) {
  val connection = project.messageBus.connect(project)
  connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
    override fun processStarted(debugProcess: XDebugProcess) {
      val xDebugSessionImpl = debugProcess.session as? XDebugSessionImpl
      val runnerAndConfigurationSettings = xDebugSessionImpl?.executionEnvironment?.runnerAndConfigurationSettings
      val currentFilePath = (runnerAndConfigurationSettings as? RunnerAndConfigurationSettingsImpl)?.filePathIfRunningCurrentFile
      if (currentFilePath != pathToRunningFile) return

      debugProcess.session.addSessionListener(object : XDebugSessionListener {
        override fun sessionPaused() {
          val pauseAction = ActionManager.getInstance().getAction(XDebuggerActions.PAUSE)

          ApplicationManager.getApplication().executeOnPooledThread {
            val toolbarForTooltip = LearningUiUtil.findComponentOrNull(project, ActionToolbarImpl::class.java) { toolbar ->
              toolbar.actionGroup.getChildren(null).any { it == pauseAction } && toolbar.place == ActionPlaces.DEBUGGER_TOOLBAR
            }
            if (toolbarForTooltip != null) {
              invokeLater {
                val stepInIcon = """<icon src="AllIcons.Actions.TraceInto"/>"""
                val resumeIcon = """<icon src="AllIcons.Actions.Resume"/>"""
                val stopIcon = """<icon src="AllIcons.Actions.Suspend"/>"""

                GotItTooltip("onboarding.tips.debug.panel.got.it", LearnBundle.message("onboarding.debug.got.it.text", stepInIcon, resumeIcon, stopIcon))
                  .withHeader(LearnBundle.message("onboarding.debug.got.it.header"))
                  .withPosition(Balloon.Position.above)
                  .show(toolbarForTooltip.component, GotItTooltip.TOP_MIDDLE)
                project.onboardingTipsDebugPath = null
                connection.disconnect()
              }
            }
          }
        }
      })
    }
  })
}
