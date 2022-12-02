// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.onboarding

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.wizard.NewProjectOnboardingTips
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.GotItTooltip
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import org.jetbrains.annotations.NonNls
import training.learn.LearnBundle
import training.ui.LearningUiUtil
import java.util.function.Function
import javax.swing.JComponent

private var onboardingGenerationNumber: Int
  get() = PropertiesComponent.getInstance().getInt("onboarding.generation.number", 0)
  set(value) { PropertiesComponent.getInstance().setValue("onboarding.generation.number", value, 0) }

private var onboardingGenerationShowDisableMessage: Boolean
  get() = PropertiesComponent.getInstance().getBoolean("onboarding.generation.show.disable.message", true)
  set(value) { PropertiesComponent.getInstance().setValue("onboarding.generation.show.disable.message", value, true) }

private var Project.onboardingTipsDebugPath: String?
  get() = PropertiesComponent.getInstance(this).getValue("onboarding.tips.debug.path")
  set(value) { PropertiesComponent.getInstance(this).setValue("onboarding.tips.debug.path", value) }

private val RESET_TOOLTIP_SAMPLE_TEXT = Key.create<String>("reset.tooltip.sample.text")

private class NewProjectOnboardingTipsImpl : NewProjectOnboardingTips {
  @RequiresEdt
  override fun installTips(project: Project, simpleSampleText: String) {
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

    val number = onboardingGenerationNumber
    if (number != 0 && onboardingGenerationShowDisableMessage) {
      RESET_TOOLTIP_SAMPLE_TEXT.set(file, simpleSampleText)
    }
    onboardingGenerationNumber = number + 1
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

private class DoNotGenerateTipsNotification : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val simpleSampleText = RESET_TOOLTIP_SAMPLE_TEXT.get(file) ?: return null
    return Function { editor ->
      EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
        text = LearnBundle.message("onboarding.propose.to.disable.tips")
        createActionLabel(LearnBundle.message("onboarding.disable.tips.action")) {
          disableNotification(file, project)

          PropertiesComponent.getInstance().setValue(NewProjectWizardStep.GENERATE_ONBOARDING_TIPS_NAME, false)

          val document = (editor as? TextEditor)?.editor?.document ?: return@createActionLabel
          DocumentUtil.writeInRunUndoTransparentAction {
            document.setText(simpleSampleText)
          }
        }
        setCloseAction {
          disableNotification(file, project)
          onboardingGenerationShowDisableMessage = false
        }
      }
    }
  }

  private fun disableNotification(file: VirtualFile, project: Project) {
    RESET_TOOLTIP_SAMPLE_TEXT.set(file, null)
    EditorNotifications.getInstance(project).updateNotifications(this)
  }
}
