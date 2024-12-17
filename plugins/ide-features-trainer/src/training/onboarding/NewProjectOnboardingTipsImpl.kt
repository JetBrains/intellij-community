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
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.ui.GotItTooltip
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.getAvailableLineBreakpointTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import training.learn.LearnBundle
import training.ui.LearningUiUtil

private var onboardingGenerationNumber: Int
  get() = PropertiesComponent.getInstance().getInt("onboarding.generation.number", 0)
  set(value) { PropertiesComponent.getInstance().setValue("onboarding.generation.number", value, 0) }


private val Project.oldFilePathWithOnboardingTips: String?
  get() = PropertiesComponent.getInstance(this).getValue("onboarding.tips.debug.path")

var Project.filePathsWithOnboardingTips: List<String>?
  @ApiStatus.Internal
  get() = PropertiesComponent.getInstance(this).getList("file.paths.with.onboarding.tips") ?: oldFilePathWithOnboardingTips?.let { listOf(it) }
  private set(value) { PropertiesComponent.getInstance(this).setList("file.paths.with.onboarding.tips", value) }

var Project.filePathsWithOnboardingBreakPointGotIt: List<String>?
  @ApiStatus.Internal
  get() = PropertiesComponent.getInstance(this).getList("file.paths.with.onboarding.breakpoint.got.it") ?: oldFilePathWithOnboardingTips?.let { listOf(it) }
  private set(value) { PropertiesComponent.getInstance(this).setList("file.paths.with.onboarding.breakpoint.got.it", value) }


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

@Service(Service.Level.PROJECT)
private class InstallOnboardingTipsService(val coroutineScope: CoroutineScope)


private fun installTipsReadAction(
  project: Project,
  info: OnboardingTipsInstallationInfo,
) {
  OnboardingTipsStatistics.logOnboardingTipsInstalled(project, onboardingGenerationNumber)

  // Set this option explicitly, because its default depends on the number of empty projects.
  PropertiesComponent.getInstance().setValue(NewProjectWizardStep.GENERATE_ONBOARDING_TIPS_NAME, true)

  val module = project.modules.singleOrNull() ?: return

  val pathsOfFilesWithTips = mutableListOf<String>()
  val pathsOfFilesWithBreakpoints = mutableListOf<String>()

  val sourceRoots = module.rootManager.let { rootManager ->
    rootManager.sourceRoots.takeIf { it.isNotEmpty() } ?: rootManager.contentRoots.takeIf { it.isNotEmpty() }
  } ?: error("No roots for $module")

  for (root in sourceRoots) {
    VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>(NO_FOLLOW_SYMLINKS, limit(10)) {
      override fun visitFileEx(file: VirtualFile): Result {
        val infoForFile = info.infos.singleOrNull { file.name == it.fileName }
        if (infoForFile == null) return CONTINUE

        val document = FileDocumentManager.getInstance().getDocument(file) ?: return CONTINUE

        val offsets = infoForFile.offsetsForBreakpoint(document.charsSequence)

        for (offset in offsets) {
          val position = XDebuggerUtil.getInstance().createPositionByOffset(file, offset) ?: return CONTINUE

          val types = getAvailableLineBreakpointTypes(project, position, null)
          XDebuggerUtilImpl.toggleAndReturnLineBreakpoint(project, types, position, false, false, null, true)
        }
        if (offsets.isNotEmpty()) {
          pathsOfFilesWithBreakpoints.add(file.path)
        }
        pathsOfFilesWithTips.add(file.path)
        return CONTINUE
      }
    })
  }
  project.filePathsWithOnboardingBreakPointGotIt = pathsOfFilesWithBreakpoints
  installDebugGotItListener(project)

  project.filePathsWithOnboardingTips = pathsOfFilesWithTips
  installActionListener(project, pathsOfFilesWithTips)

  project.putUserData(onboardingTipsInstallationInfoKey, null)

  onboardingGenerationNumber++
}

private class InstallOnboardingTipsEditorListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project ?: return

    val info = onboardingTipsInstallationInfoKey.get(project)
    if (info != null) {
      project.service<InstallOnboardingTipsService>().coroutineScope.launch {
        readAction {
          installTipsReadAction(project, info)
        }
      }
    }

    // It may take some time after the editor will be opened, but the onboarding tips are not installed yet (some race may be here)
    if (info != null || editor.virtualFile?.path?.let { project.filePathsWithOnboardingTips?.contains(it) } == true) {
      DocRenderManager.setDocRenderingEnabled(editor, true)
    }
  }
}

private fun installActionListener(project: Project, pathsOfFilesWithTips: MutableList<String>) {
  val connection = project.messageBus.connect()
  val actionsMapReported = promotedActions.associateWith { false }.toMutableMap()
  connection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
      val virtualFile = event.getData(CommonDataKeys.EDITOR)?.virtualFile ?: return
      if (!pathsOfFilesWithTips.contains(virtualFile.path)) {
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

private fun installDebugGotItListener(project: Project) {
  val pathsToFilesWithBreakpoints = project.filePathsWithOnboardingBreakPointGotIt ?: return
  if (pathsToFilesWithBreakpoints.isEmpty()) return
  val connection = project.messageBus.connect()
  connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
    override fun processStarted(debugProcess: XDebugProcess) {
      val xDebugSessionImpl = debugProcess.session as? XDebugSessionImpl
      val runnerAndConfigurationSettings = xDebugSessionImpl?.executionEnvironment?.runnerAndConfigurationSettings
      val currentFilePath = (runnerAndConfigurationSettings as? RunnerAndConfigurationSettingsImpl)?.filePathIfRunningCurrentFile
      if (!pathsToFilesWithBreakpoints.contains(currentFilePath)) return

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

                project.filePathsWithOnboardingBreakPointGotIt = null
                connection.disconnect()
              }
            }
          }
        }
      })
    }
  })
}
