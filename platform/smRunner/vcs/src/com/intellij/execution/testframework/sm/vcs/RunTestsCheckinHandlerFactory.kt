// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
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
import com.intellij.openapi.vcs.checkin.BaseCommitCheck
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CommitProblem
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.NullCommitWorkflowHandler
import com.intellij.vcs.commit.isBackgroundCommitChecks
import com.intellij.vcs.commit.isNonModalCommit
import kotlinx.coroutines.*
import org.jetbrains.annotations.NotNull
import javax.swing.JComponent
import kotlin.coroutines.resume

private val LOG = logger<RunTestsCheckinHandlerFactory>()

@State(name = "TestsVcsConfig", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class TestsVcsConfiguration : PersistentStateComponent<TestsVcsConfiguration.MyState> {
  class MyState {
    var enabled = false
    var configuration : ConfigurationBean? = null
  }
  var myState = MyState()
  override fun getState() = myState

  override fun loadState(state: MyState) {
    myState = state
  }
}

class RunTestsCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return if (isBackgroundCommitChecks() && (panel.isNonModalCommit || panel.commitWorkflowHandler is NullCommitWorkflowHandler)) RunTestsBeforeCheckinHandler(panel) else CheckinHandler.DUMMY
  }
}

class FailedTestCommitProblem(val problems: List<FailureDescription>) : CommitProblem {
  override val text: String
    get() {
      var str = ""
      val failed = problems.sumBy { it.failed }
      if (failed > 0) {
        str = TestRunnerBundle.message("tests.result.failure.summary", failed)
      }

      val ignored = problems.sumBy { it.ignored }
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
}

data class FailureDescription(val historyFileName: String, val failed: Int, val ignored: Int, val configuration: RunnerAndConfigurationSettings?, val configName: String?)

private fun createCommitProblem(descriptions: List<FailureDescription>): FailedTestCommitProblem? =
  if (descriptions.isNotEmpty()) FailedTestCommitProblem(descriptions) else null

class RunTestsBeforeCheckinHandler(private val commitPanel: CheckinProjectPanel) : BaseCommitCheck<FailedTestCommitProblem>() {
  private val project: Project get() = commitPanel.project
  private val settings: TestsVcsConfiguration get() = project.getService(TestsVcsConfiguration::class.java)

  override fun isEnabled(): Boolean = settings.myState.enabled

  override suspend fun doRunCheck(): FailedTestCommitProblem? {
    val configurationBean = settings.myState.configuration ?: return null
    val configurationSettings = RunManager.getInstance(project).findConfigurationByTypeAndName(configurationBean.configurationId, configurationBean.name)
    if (configurationSettings == null) {
      return createCommitProblem(listOf(FailureDescription("", 0, 0, configurationSettings, configurationBean.name)))
    }
    progress(name = SmRunnerBundle.message("progress.text.running.tests", configurationSettings.name))

    return withContext(Dispatchers.IO) {
      val problems = ArrayList<FailureDescription>()
      val executor = DefaultRunExecutor.getRunExecutorInstance()
      val configuration = configurationSettings.configuration
      if (configuration is CompoundRunConfiguration) {
        val runManager = RunManagerImpl.getInstanceImpl(project)
        configuration.getConfigurationsWithTargets(runManager)
          .map { runManager.findSettings(it.key) }
          .filterNotNull()
          .forEach { startConfiguration(executor, it, problems) }
      }
      else {
        startConfiguration(executor, configurationSettings, problems)
      }
      
      return@withContext createCommitProblem(problems) 
    }
  }

  private suspend fun startConfiguration(executor: Executor,
                                         configurationSettings: RunnerAndConfigurationSettings,
                                         problems: ArrayList<FailureDescription>) {
    val environmentBuilder = ExecutionUtil.createEnvironment(executor, configurationSettings) ?: return
    val executionTarget = ExecutionTargetManager.getInstance(project).findTarget(configurationSettings.configuration)
    val environment = environmentBuilder.target(executionTarget).build()
    environment.setHeadless()
    val console = suspendCancellableCoroutine<ExecutionConsole?> { continuation ->
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
          onProcessStarted(it, continuation)
        }
      }
    } ?: return

    val form = console.resultsForm
    if (form != null) {
      reportProblem(form, problems, configurationSettings)
      if (!isSuccessful(form.testsRootNode)) {
        awaitSavingHistory(form.historyFileName)
      }
    }

    disposeConsole(console)
  }

  private fun onProcessStarted(descriptor: RunContentDescriptor, continuation: CancellableContinuation<ExecutionConsole?>) {
    val handler = descriptor.processHandler
    if (handler != null) {
      val executionConsole = descriptor.console
      val processListener = object : ProcessAdapter() {
        override fun processTerminated(event: ProcessEvent) = continuation.resume(executionConsole)
      }

      handler.addProcessListener(processListener)
      executionConsole?.resultsForm?.addEventsListener(object : TestResultsViewer.EventsListener {
        override fun onTestNodeAdded(sender: TestResultsViewer, test: SMTestProxy) = progress(details = test.getFullName())
      })

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
      while (getHistoryFile(historyFileName).second == null) {
        delay(timeMillis = 500)
      }
      DumbService.getInstance(project).waitForSmartMode()
    }
  }

  override fun showDetails(problem: FailedTestCommitProblem) {
    val groupId = ExecutionEnvironment.getNextUnusedExecutionId()
    for (p in problem.problems) {
      if (p.historyFileName.isEmpty() && p.failed == 0 && p.ignored == 0) {
        if (p.configuration != null) {
          RunDialog.editConfiguration(project, p.configuration, ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", p.configuration.name))
          continue
        }
        if (p.configName != null) {
          EditConfigurationsDialog(project).show()
          continue
        }
      }
      val (path, virtualFile) = getHistoryFile(p.historyFileName)
      if (virtualFile != null) {
        AbstractImportTestsAction.doImport(project, virtualFile, groupId)
      }
      else {
        LOG.error("File not found: $path")
      }
    }
  }

  private fun getHistoryFile(fileName: String): Pair<String, VirtualFile?> {
    val path = "${TestStateStorage.getTestHistoryRoot(project).path}/$fileName.xml"
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
    return Pair(path, virtualFile)
  }


  @NlsContexts.DialogTitle
  private fun getInitialText(): String {
    val configurationBean = settings.myState.configuration
    return if (configurationBean != null) getOptionTitle(configurationBean.name)
    else SmRunnerBundle.message("checkbox.run.tests.before.commit.no.configuration")
  }

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
    
    return object :
      BooleanCommitOption(commitPanel, getInitialText(), true, settings.myState::enabled) {
      
      
      override fun getComponent(): JComponent {
        val showFiltersPopup = LinkListener<Any> { sourceLink, _ ->
          JBPopupMenu.showBelow(sourceLink, ActionPlaces.UNKNOWN, createConfigurationChooser())
        }
        val configureFilterLink = LinkLabel(SmRunnerBundle.message("link.label.choose.configuration.before.commit"), null, showFiltersPopup)
  
        checkBox.text = getInitialText()
        return JBUI.Panels.simplePanel(4, 0).addToLeft(checkBox).addToCenter(configureFilterLink)
      }

      private fun createConfigurationChooser(): ActionGroup {
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

                    checkBox.text = getOptionTitle(configuration.name)
                  }
                })
              }
          }
        }
        return result
      }
    }
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

  private fun reportProblem(resultsForm: SMTestRunnerResultsForm,
                            problems: ArrayList<FailureDescription>,
                            configuration: RunnerAndConfigurationSettings) {
    val rootNode = resultsForm.testsRootNode
    if (!isSuccessful(rootNode)) {
      val presentation = TestResultPresentation(rootNode).presentation
      problems.add(FailureDescription(resultsForm.historyFileName, presentation.failedCount, presentation.ignoredCount, configuration, configuration.name))
    }
  }

  private fun isSuccessful(rootNode: @NotNull SMTestProxy.SMRootTestProxy) : Boolean =
    !rootNode.isDefect && rootNode.children.isNotEmpty()
}