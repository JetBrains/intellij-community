// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.vcs

import com.intellij.build.BuildView
import com.intellij.execution.*
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.impl.RunConfigurationBeforeRunProviderDelegate
import com.intellij.execution.impl.RunDialog
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.TestsUIUtil.TestResultPresentation
import com.intellij.execution.testframework.sm.ConfigurationBean
import com.intellij.execution.testframework.sm.SmRunnerBundle
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.forEachWithProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.containers.addIfNotNull
import com.intellij.vcs.commit.NullCommitWorkflowHandler
import com.intellij.vcs.commit.isNonModalCommit
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import kotlin.coroutines.resume

private val LOG = logger<RunTestsCheckinHandlerFactory>()

@Service(Service.Level.PROJECT)
@State(name = "TestsVcsConfig", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
private class TestsVcsConfiguration : PersistentStateComponent<TestsVcsConfiguration.MyState> {
  class MyState {
    var enabled = false
    var configuration: ConfigurationBean? = null
  }

  var myState = MyState()
  override fun getState() = myState

  override fun loadState(state: MyState) {
    myState = state
  }
}

private class RunTestsCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    if (panel.isNonModalCommit || panel.commitWorkflowHandler is NullCommitWorkflowHandler) {
      return RunTestsBeforeCheckinHandler(panel.project)
    }
    return CheckinHandler.DUMMY
  }
}

private class RunConfigurationMultipleProblems(val problems: List<FailureDescription>) : CommitProblem {
  override val text: @NlsSafe String
    get() = problems.sortedBy {
      when (it) {
        is FailureDescription.TestsFailed -> 0
        is FailureDescription.ProcessNonZeroExitCode -> 1
        is FailureDescription.FailedToStart -> 2
      }
    }.joinToString("\n") { it.message }
}

private class RunConfigurationProblemWithDetails(val problem: FailureDescription) : CommitProblemWithDetails {
  override val text: String
    get() = problem.message

  override fun showDetails(project: Project) {
    when (problem) {
      is FailureDescription.FailedToStart -> {
        if (problem.configuration != null)
          RunDialog.editConfiguration(project,
                                      problem.configuration,
                                      ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", problem.configuration.name))
        else EditConfigurationsDialog(project).show()
      }
      is FailureDescription.ProcessNonZeroExitCode -> {
        RunDialog.editConfiguration(project, problem.configuration,
                                    ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", problem.configuration.name))
      }
      is FailureDescription.TestsFailed -> {
        val (path, virtualFile) = getHistoryFile(project, problem.historyFileName)
        if (virtualFile != null) {
          AbstractImportTestsAction.doImport(project, virtualFile, ExecutionEnvironment.getNextUnusedExecutionId())
        }
        else {
          LOG.error("File not found: $path")
        }
      }
    }
  }

  override val showDetailsAction: String
    get() =
      if (problem is FailureDescription.TestsFailed) ExecutionBundle.message("commit.checks.run.configuration.failed.show.details.action")
      else VcsBundle.message("before.commit.run.configuration.failed.edit.configuration")
}

private sealed class FailureDescription(val configName: String) {
  abstract val message: @Nls String

  class FailedToStart(configName: String, val configuration: RunnerAndConfigurationSettings?) : FailureDescription(configName) {
    override val message
      get() = VcsBundle.message("before.commit.run.configuration.failed.to.start", configName)
  }
  class TestsFailed(configName: String, val historyFileName: String, val testsResultText: String) : FailureDescription(configName) {
    override val message
      get() = VcsBundle.message("before.commit.run.configuration.tests.failed", configName, testsResultText)
  }
  class ProcessNonZeroExitCode(configName: String, val configuration: RunnerAndConfigurationSettings, val exitCode: Int) : FailureDescription(configName) {
    override val message
      get() = VcsBundle.message("before.commit.run.configuration.failed", configName, exitCode)
  }
}

private class RunTestsBeforeCheckinHandler(private val project: Project) : CheckinHandler(), CommitCheck {
  private val settings: TestsVcsConfiguration get() = project.getService(TestsVcsConfiguration::class.java)

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.POST_COMMIT

  override fun isEnabled(): Boolean = settings.myState.enabled

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val configurationBean = settings.myState.configuration ?: return null
    val configurationSettings = RunManager.getInstance(project).findConfigurationByTypeAndName(configurationBean.configurationId,
                                                                                               configurationBean.name)
    if (configurationSettings == null) {
      return createCommitProblem(listOf(FailureDescription.FailedToStart(configurationBean.name, configurationSettings)))
    }

    val problems = ArrayList<FailureDescription>()
    withProgressText(SmRunnerBundle.message("progress.text.running.tests", configurationSettings.name)) {
      withContext(Dispatchers.IO) {
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val configuration = configurationSettings.configuration
        if (configuration is CompoundRunConfiguration) {
          val runManager = RunManagerImpl.getInstanceImpl(project)
          configuration.getConfigurationsWithTargets(runManager).keys
            .forEachWithProgress { runConfiguration ->
              runManager.findSettings(runConfiguration)?.let { runnerAndConfigurationSettings ->
                startConfiguration(executor, runnerAndConfigurationSettings, problems)
              }
            }
        }
        else {
          startConfiguration(executor, configurationSettings, problems)
        }
      }
    }

    return createCommitProblem(problems)
  }

  private class RunConfigurationResult(val exitCode: Int, val testResultsFormDescriptor: TestResultsFormDescriptor?)

  private data class TestResultsFormDescriptor(val rootNode: SMTestProxy.SMRootTestProxy, val historyFileName: String)

  private suspend fun startConfiguration(executor: Executor,
                                         configurationSettings: RunnerAndConfigurationSettings,
                                         problems: ArrayList<FailureDescription>): Unit = reportRawProgress { reporter ->
    val environmentBuilder = ExecutionUtil.createEnvironment(executor, configurationSettings) ?: return
    val executionTarget = ExecutionTargetManager.getInstance(project).findTarget(configurationSettings.configuration)
    val environment = environmentBuilder.target(executionTarget).build()

    // Otherwise sh run configuration can be executed in the terminal resulting into processNotStarted
    RunConfigurationBeforeRunProviderDelegate.EP_NAME.extensionList.forEach { delegate ->
      delegate.beforeRun(environment)
    }

    val runConfigurationResult = suspendCancellableCoroutine<RunConfigurationResult?> { continuation ->
      ProgramRunnerUtil.executeConfigurationAsync(environment, false, true, object : ProgramRunner.Callback {
        override fun processStarted(descriptor: RunContentDescriptor?) {
          if (descriptor == null) {
            LOG.warn("Run descriptor is null for run configuration: ${configurationSettings.name}")
            continuation.resume(null)
          } else {
            onProcessStarted(reporter, descriptor, continuation)
          }
        }

        override fun processNotStarted(error: Throwable?) {
          Disposer.dispose(environment)
          problems.add(FailureDescription.FailedToStart(configurationSettings.name, configurationSettings))
          continuation.resume(null)
        }
      })
    } ?: return

    problems.addIfNotNull(getExecutionProblem(runConfigurationResult, configurationSettings))
  }

  private suspend fun getExecutionProblem(
    runConfigurationResult: RunConfigurationResult,
    configurationSettings: RunnerAndConfigurationSettings,
  ): FailureDescription? {
    if (runConfigurationResult.exitCode == 0) return null

    val rootNode = runConfigurationResult.testResultsFormDescriptor?.rootNode
    return if (rootNode != null && (rootNode.isDefect || rootNode.children.isEmpty())) {
      val fileName = runConfigurationResult.testResultsFormDescriptor.historyFileName
      val presentation = TestResultPresentation(rootNode).presentation
      awaitSavingHistory(fileName)
      FailureDescription.TestsFailed(configName = configurationSettings.name, historyFileName = fileName, testsResultText = presentation.text)
    }
    else {
      FailureDescription.ProcessNonZeroExitCode(configurationSettings.name, configurationSettings, runConfigurationResult.exitCode)
    }
  }

  private fun onProcessStarted(
    reporter: RawProgressReporter,
    descriptor: RunContentDescriptor,
    continuation: CancellableContinuation<RunConfigurationResult?>,
  ) {
    val handler = descriptor.processHandler
    if (handler != null) {
      val executionConsole = descriptor.console
      val testResultForm = executionConsole?.component as? SMTestRunnerResultsForm
      val processListener = object : ProcessAdapter() {
        override fun processTerminated(event: ProcessEvent) {
          val result = RunConfigurationResult(
            event.exitCode,
            testResultForm?.let { TestResultsFormDescriptor(it.testsRootNode, it.historyFileName) }
          )
          continuation.resume(result)
        }
      }

      handler.addProcessListener(processListener)

      testResultForm?.addEventsListener(object : TestResultsViewer.EventsListener {
        override fun onTestNodeAdded(sender: TestResultsViewer, test: SMTestProxy) = reporter.details(test.getFullName())
      })

      continuation.invokeOnCancellation {
        handler.removeProcessListener(processListener)
        handler.destroyProcess()
      }
    }
    else {
      continuation.resume(null)
    }
  }

  private fun createCommitProblem(descriptions: List<FailureDescription>): CommitProblem? {
    return when {
      descriptions.isEmpty() -> null
      descriptions.size == 1 -> RunConfigurationProblemWithDetails(descriptions.single())
      else -> RunConfigurationMultipleProblems(descriptions)
    }
  }

  private val RunContentDescriptor.console: ExecutionConsole?
    get() = executionConsole?.let { if (it is BuildView) it.consoleView else it }

  private fun SMTestProxy.getFullName(): @NlsSafe String =
    if (parent == null || parent is SMTestProxy.SMRootTestProxy) presentableName
    else parent.getFullName() + "." + presentableName

  private suspend fun awaitSavingHistory(historyFileName: String) {
    withTimeout(timeMillis = 600000) {
      while (getHistoryFile(project, historyFileName).second == null) {
        delay(timeMillis = 500)
      }
      DumbService.getInstance(project).waitForSmartMode()
    }
  }

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
    val configurationBean = settings.myState.configuration
    val initialText = when {
      configurationBean != null -> getOptionTitle(configurationBean.name)
      else -> VcsBundle.message("before.commit.run.configuration.no.configuration.selected.checkbox")
    }

    return BooleanCommitOption.createLink(project, this, disableWhenDumb = true, initialText, settings.myState::enabled,
                                          SmRunnerBundle.message("link.label.choose.configuration.before.commit")) { sourceLink, linkData ->
      JBPopupFactory.getInstance().createActionGroupPopup(null,
                                                          createConfigurationChooser(linkData),
                                                          SimpleDataContext.EMPTY_CONTEXT,
                                                          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                          false)
        .showUnderneathOf(sourceLink)
    }
  }

  private fun createConfigurationChooser(linkContext: BooleanCommitOption.LinkContext): ActionGroup {
    val result = DefaultActionGroup()
    val runManager = RunManagerImpl.getInstanceImpl(project)
    for ((type, folderMap) in runManager.getConfigurationsGroupedByTypeAndFolder(false)) {
      var addedSeparator = false
      for ((folder, list) in folderMap.entries) {
        val localConfigurations: List<RunConfiguration> = list.map { it.configuration }
        if (localConfigurations.isEmpty()) continue

        if (!addedSeparator && result.childrenCount > 0) {
          result.addSeparator()
          addedSeparator = true
        }

        val target = if (folder != null) DefaultActionGroup(folder, true).apply {
          templatePresentation.icon = AllIcons.Nodes.Folder
          result.add(this)
        }
        else result

        localConfigurations
          .forEach { configuration: RunConfiguration ->
            target.add(object : AnAction(configuration.icon) {
              init {
                templatePresentation.setText(configuration.name, false)
              }

              override fun actionPerformed(e: AnActionEvent) {
                val bean = ConfigurationBean()
                bean.configurationId = type.id
                bean.name = configuration.name
                settings.myState.configuration = bean

                val optionTitle = getOptionTitle(configuration.name)
                linkContext.setCheckboxText(optionTitle)
              }
            })
          }
      }
    }
    return result
  }

  @NlsContexts.DialogTitle
  private fun getOptionTitle(name: String): String {
    return SmRunnerBundle.message("checkbox.run.tests.before.commit", name)
  }
}

private fun getHistoryFile(project: Project, fileName: String): Pair<String, VirtualFile?> {
  val path = "${TestStateStorage.getTestHistoryRoot(project).path}/$fileName.xml"
  val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
  return Pair(path, virtualFile)
}
