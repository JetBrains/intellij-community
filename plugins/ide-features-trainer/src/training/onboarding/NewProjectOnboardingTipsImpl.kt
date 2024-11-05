// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package training.onboarding

import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.wizard.NewProjectOnboardingTips
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.OnboardingTipsInstallationInfo
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.getDelegateChainRootAction
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.GotItTooltip
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.getAvailableLineBreakpointTypes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import training.learn.LearnBundle
import training.ui.LearningUiUtil

private var onboardingGenerationNumber: Int
  get() = PropertiesComponent.getInstance().getInt("onboarding.generation.number", 0)
  set(value) { PropertiesComponent.getInstance().setValue("onboarding.generation.number", value, 0) }

var Project.filePathWithOnboardingTips: String?
  @ApiStatus.Internal
  get() = PropertiesComponent.getInstance(this).getValue("onboarding.tips.debug.path")
  @ApiStatus.Internal
  set(value) { PropertiesComponent.getInstance(this).setValue("onboarding.tips.debug.path", value) }


val renderedOnboardingTipsEnabled: Boolean
  @ApiStatus.Internal
  get() = Registry.`is`("doc.onboarding.tips.render")

internal val promotedActions = listOf(IdeActions.ACTION_SEARCH_EVERYWHERE,
                                      IdeActions.ACTION_SHOW_INTENTION_ACTIONS,
                                      IdeActions.ACTION_DEFAULT_RUNNER,
                                      "RunClass",
                                      IdeActions.ACTION_DEFAULT_DEBUGGER,
                                      "DebugClass",
                                      IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)

// The presence of this data means that we need to install onboarding tips on the first editor in the new created project
private val onboardingTipsInstallationInfoKey = Key<OnboardingTipsInstallationInfo>("onboardingTipsInstallationInfo")

private class NewProjectOnboardingTipsImpl : NewProjectOnboardingTips {
  @RequiresEdt
  override fun installTips(project: Project, info: OnboardingTipsInstallationInfo) {
    project.putUserData(onboardingTipsInstallationInfoKey, info)
  }
}

private fun installTipsInFirstEditor(editor: Editor, project: Project, info: OnboardingTipsInstallationInfo) {
  OnboardingTipsStatistics.logOnboardingTipsInstalled(project, onboardingGenerationNumber)

  // Set this option explicitly, because its default depends on the number of empty projects.
  PropertiesComponent.getInstance().setValue(NewProjectWizardStep.GENERATE_ONBOARDING_TIPS_NAME, true)

  val file = editor.virtualFile ?: return

  val document = FileDocumentManager.getInstance().getDocument(file) ?: return

  val offset = info.offsetForBreakpoint(document.charsSequence)

  if (offset != null) {
    val position = XDebuggerUtil.getInstance().createPositionByOffset(file, offset) ?: return

    val types = getAvailableLineBreakpointTypes(project, position, null)
    XDebuggerUtilImpl.toggleAndReturnLineBreakpoint(project, types, position, false, false, null, true)
  }

  val pathToRunningFile = file.path
  project.filePathWithOnboardingTips = pathToRunningFile
  installDebugListener(project, pathToRunningFile)
  installActionListener(project, pathToRunningFile)

  onboardingGenerationNumber++
}

private class InstallOnboardingTooltip : ProjectActivity {
  override suspend fun execute(project: Project) {
    val pathToRunningFile = project.filePathWithOnboardingTips ?: return
    installDebugListener(project, pathToRunningFile)
  }
}

private class InstallOnboardingTipsEditorListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project ?: return

    val info = onboardingTipsInstallationInfoKey.get(project)

    if (info != null) {
      if (editor.virtualFile?.name != info.fileName) return
      project.putUserData(onboardingTipsInstallationInfoKey, null)
      installTipsInFirstEditor(editor, project, info)
    } else {
      val pathToRunningFile = project.filePathWithOnboardingTips ?: return
      if (editor.virtualFile?.path != pathToRunningFile) return
    }
    DocRenderManager.setDocRenderingEnabled(editor, true)
  }
}

private fun installActionListener(project: Project, pathToRunningFile: @NonNls String) {
  val connection = project.messageBus.connect()
  val actionsMapReported = promotedActions.associateWith { false }.toMutableMap()
  connection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
      val virtualFile = event.getData(CommonDataKeys.EDITOR)?.virtualFile ?: return
      if (virtualFile.path != pathToRunningFile) {
        return
      }

      val original = getDelegateChainRootAction(action)
      val actionId = ActionManager.getInstance().getId(original) ?: return
      val reported = actionsMapReported.get(actionId) ?: return
      if (!reported) {
        actionsMapReported.put(actionId, true)
        // usage count increased in the beforeActionPerformed listener,
        // so here we use afterActionPerformed event to avoid question about listeners order
        val usageCount = service<ActionsLocalSummary>().getActionStatsById(actionId)?.usageCount ?: return
        OnboardingTipsStatistics.logPromotedActionUsedEvent(project, actionId, onboardingGenerationNumber, usageCount == 1)
      }
    }
  })
}

private fun installDebugListener(project: Project, pathToRunningFile: @NonNls String) {
  val connection = project.messageBus.connect()
  connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
    override fun processStarted(debugProcess: XDebugProcess) {
      val xDebugSessionImpl = debugProcess.session as? XDebugSessionImpl
      val runnerAndConfigurationSettings = xDebugSessionImpl?.executionEnvironment?.runnerAndConfigurationSettings
      val currentFilePath = (runnerAndConfigurationSettings as? RunnerAndConfigurationSettingsImpl)?.filePathIfRunningCurrentFile
      if (currentFilePath != pathToRunningFile) return

      debugProcess.session.addSessionListener(object : XDebugSessionListener {
        override fun sessionPaused() {
          val targetAction = ActionManager.getInstance().getAction(XDebuggerActions.STEP_INTO)

          ApplicationManager.getApplication().executeOnPooledThread {
            val targetComponent = LearningUiUtil.findComponentOrNull(project, ActionButton::class.java) { button ->
              button.action == targetAction
            }
            if (targetComponent != null) {
              invokeLater {
                val stepInIcon = """<icon src="AllIcons.Actions.TraceInto"/>"""
                val resumeIcon = """<icon src="AllIcons.Actions.Resume"/>"""
                val stopIcon = """<icon src="AllIcons.Actions.Suspend"/>"""

                GotItTooltip("onboarding.tips.debug.panel.got.it", LearnBundle.message("onboarding.debug.got.it.text", stepInIcon, resumeIcon, stopIcon))
                  .withHeader(LearnBundle.message("onboarding.debug.got.it.header"))
                  .withPosition(Balloon.Position.above)
                  .show(targetComponent, GotItTooltip.TOP_MIDDLE)
                connection.disconnect()
              }
            }
          }
        }
      })
    }
  })
}
