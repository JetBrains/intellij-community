// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.vcs

import com.intellij.build.BuildView
import com.intellij.execution.*
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.impl.RunDialog
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.testframework.TestRunnerBundle
import com.intellij.execution.testframework.TestsUIUtil.TestResultPresentation
import com.intellij.execution.testframework.actions.ConsolePropertiesProvider
import com.intellij.execution.testframework.sm.ConfigurationBean
import com.intellij.execution.testframework.sm.SmRunnerBundle
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer
import com.intellij.execution.testframework.sm.vcs.RunTestsBeforeCheckinHandler.Companion.showFailedTests
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.CheckinProjectPanel
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
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.NullCommitWorkflowHandler
import com.intellij.vcs.commit.isNonModalCommit
import kotlinx.coroutines.*
import kotlin.coroutines.resume

private val LOG = logger<RunTestsCheckinHandlerFactory>()

@Service(Service.Level.PROJECT)
@State(name = "TestsVcsConfig", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class TestsVcsConfiguration : PersistentStateComponent<TestsVcsConfiguration.MyState> {
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

class RunTestsCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    if (panel.isNonModalCommit || panel.commitWorkflowHandler is NullCommitWorkflowHandler) {
      return RunTestsBeforeCheckinHandler(panel.project)
    }
    return CheckinHandler.DUMMY
  }
}

class FailedTestCommitProblem(val problems: List<FailureDescription>) : CommitProblemWithDetails {
  override val text: String
    get() {
      var str = ""
      val failed = problems.sumOf { it.failed }
      if (failed > 0) {
        str = TestRunnerBundle.message("tests.result.failure.summary", failed)
      }

      val ignored = problems.sumOf { it.ignored }
      if (ignored > 0) {
        str += (if (failed > 0) ", " else "")
        str += TestRunnerBundle.message("tests.result.ignore.summary", ignored)
      }

      val failedToStartMessages = problems
        .filter { it.failed == 0 && it.ignored == 0 }
        .mapNotNull { it.configName }
        .joinToString { TestRunnerBundle.message("failed.to.start.message", it) }
      if (failedToStartMessages.isNotEmpty()) {
        str += (if (ignored + failed > 0) ", " else "")
        str += failedToStartMessages
      }
      return str
    }

  override fun showDetails(project: Project) {
    showFailedTests(project, this)
  }

  override val showDetailsAction: String
    get() = ExecutionBundle.message("commit.checks.run.configuration.failed.show.details.action")
}

data class FailureDescription(val historyFileName: String,
                              val failed: Int,
                              val ignored: Int,
                              val configuration: RunnerAndConfigurationSettings?,
                              val configName: String?)

private fun createCommitProblem(descriptions: List<FailureDescription>): FailedTestCommitProblem? =
  if (descriptions.isNotEmpty()) FailedTestCommitProblem(descriptions) else null

class RunTestsBeforeCheckinHandler(private val project: Project) : CheckinHandler(), CommitCheck {
  private val settings: TestsVcsConfiguration get() = project.getService(TestsVcsConfiguration::class.java)

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.POST_COMMIT

  override fun isEnabled(): Boolean = settings.myState.enabled

  override suspend fun runCheck(commitInfo: CommitInfo): FailedTestCommitProblem? {
    val configurationBean = settings.myState.configuration ?: return null
    val configurationSettings = RunManager.getInstance(project).findConfigurationByTypeAndName(configurationBean.configurationId,
                                                                                               configurationBean.name)
    if (configurationSettings == null) {
      return createCommitProblem(listOf(FailureDescription("", 0, 0, configuration = null, configurationBean.name)))
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

  private data class TestResultsFormDescriptor(val executionConsole: ExecutionConsole,
                                               val rootNode: SMTestProxy.SMRootTestProxy,
                                               val historyFileName: String)

  private suspend fun startConfiguration(executor: Executor,
                                         configurationSettings: RunnerAndConfigurationSettings,
                                         problems: ArrayList<FailureDescription>): Unit = reportRawProgress { reporter ->
    val environmentBuilder = ExecutionUtil.createEnvironment(executor, configurationSettings) ?: return
    val executionTarget = ExecutionTargetManager.getInstance(project).findTarget(configurationSettings.configuration)
    val environment = environmentBuilder.target(executionTarget).build()
    environment.setHeadless()
    val formDescriptor = suspendCancellableCoroutine<TestResultsFormDescriptor?> { continuation ->
      val messageBus = project.messageBus
      messageBus.connect(environment).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
        override fun processNotStarted(executorId: String, env: ExecutionEnvironment) {
          if (environment.executionId == env.executionId) {
            Disposer.dispose(environment)
            problems.add(FailureDescription("", 0, 0, configurationSettings, configurationSettings.name))
            continuation.resume(null)
          }
        }
      })
      ProgramRunnerUtil.executeConfigurationAsync(environment, false, true) {
        if (it != null) {
          onProcessStarted(reporter, it, continuation)
        }
      }
    } ?: return

    val rootNode = formDescriptor.rootNode
    if (rootNode.isDefect || rootNode.children.isEmpty()) {
      val fileName = formDescriptor.historyFileName
      val presentation = TestResultPresentation(rootNode).presentation
      problems.add(FailureDescription(fileName, presentation.failedCount, presentation.ignoredCount, configurationSettings,
                                      configurationSettings.name))
      awaitSavingHistory(fileName)
    }

    disposeConsole(formDescriptor.executionConsole)
  }

  private fun onProcessStarted(reporter: RawProgressReporter?,
                               descriptor: RunContentDescriptor,
                               continuation: CancellableContinuation<TestResultsFormDescriptor?>) {
    val handler = descriptor.processHandler
    if (handler != null) {
      val executionConsole = descriptor.console
      val resultsForm = executionConsole?.resultsForm
      val formDescriptor = when {
        resultsForm != null -> TestResultsFormDescriptor(executionConsole, resultsForm.testsRootNode, resultsForm.historyFileName)
        else -> null
      }
      val processListener = object : ProcessAdapter() {
        override fun processTerminated(event: ProcessEvent) = continuation.resume(formDescriptor)
      }

      handler.addProcessListener(processListener)
      if (reporter != null) {
        resultsForm?.addEventsListener(object : TestResultsViewer.EventsListener {
          override fun onTestNodeAdded(sender: TestResultsViewer, test: SMTestProxy) = reporter.details(test.getFullName())
        })
      }

      continuation.invokeOnCancellation {
        handler.removeProcessListener(processListener)
        handler.destroyProcess()
        executionConsole?.let { disposeConsole(it) }
      }
    }
    else {
      continuation.resume(null)
    }
  }

  private val ExecutionConsole.resultsForm: SMTestRunnerResultsForm? get() = component as? SMTestRunnerResultsForm

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

  companion object {
    internal fun showFailedTests(project: Project, problem: FailedTestCommitProblem) {
      val groupId = ExecutionEnvironment.getNextUnusedExecutionId()
      for (p in problem.problems) {
        if (p.historyFileName.isEmpty() && p.failed == 0 && p.ignored == 0) {
          if (p.configuration != null) {
            RunDialog.editConfiguration(project, p.configuration,
                                        ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", p.configuration.name))
            continue
          }
          if (p.configName != null) {
            EditConfigurationsDialog(project).show()
            continue
          }
        }
        val (path, virtualFile) = getHistoryFile(project, p.historyFileName)
        if (virtualFile != null) {
          AbstractImportTestsAction.doImport(project, virtualFile, groupId)
        }
        else {
          LOG.error("File not found: $path")
        }
      }
    }

    private fun getHistoryFile(project: Project, fileName: String): Pair<String, VirtualFile?> {
      val path = "${TestStateStorage.getTestHistoryRoot(project).path}/$fileName.xml"
      val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
      return Pair(path, virtualFile)
    }
  }

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
    val configurationBean = settings.myState.configuration
    val initialText = when {
      configurationBean != null -> getOptionTitle(configurationBean.name)
      else -> SmRunnerBundle.message("checkbox.run.tests.before.commit.no.configuration")
    }

    return BooleanCommitOption.createLink(project, this, disableWhenDumb = true, initialText, settings.myState::enabled,
                                          SmRunnerBundle.message("link.label.choose.configuration.before.commit")) { sourceLink, linkData ->
      JBPopupMenu.showBelow(sourceLink, ActionPlaces.UNKNOWN, createConfigurationChooser(linkData))
    }
  }

  private fun createConfigurationChooser(linkContext: BooleanCommitOption.LinkContext): ActionGroup {
    fun testConfiguration(it: RunConfiguration) =
      it is ConsolePropertiesProvider && it.createTestConsoleProperties(DefaultRunExecutor.getRunExecutorInstance()) != null

    val result = DefaultActionGroup()
    val runManager = RunManagerImpl.getInstanceImpl(project)
    for ((type, folderMap) in runManager.getConfigurationsGroupedByTypeAndFolder(false)) {
      var addedSeparator = false
      for ((folder, list) in folderMap.entries) {
        val localConfigurations: List<RunConfiguration> = list.map { it.configuration }
          .filter {
            testConfiguration(it) ||
            it is CompoundRunConfiguration && it.getConfigurationsWithTargets(runManager).keys.all { one -> testConfiguration(one) }
          }

        if (localConfigurations.isEmpty()) continue

        if (!addedSeparator && result.childrenCount > 0) {
          result.addSeparator()
          addedSeparator = true
        }

        var target = result
        if (folder != null) {
          target = DefaultActionGroup(folder, true)
          result.add(target)
        }


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

  private fun disposeConsole(executionConsole: ExecutionConsole) {
    UIUtil.invokeLaterIfNeeded {
      Disposer.dispose(executionConsole)
    }
  }

}