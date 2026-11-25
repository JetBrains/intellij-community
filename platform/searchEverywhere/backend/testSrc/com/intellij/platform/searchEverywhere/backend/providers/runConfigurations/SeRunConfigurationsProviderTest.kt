// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.runConfigurations

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.presentations.SeBasicItemPresentationImpl
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
import com.intellij.platform.searchEverywhere.toProviderId
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.util.UUID
import javax.swing.Icon

private const val RUN_EXECUTOR_ID: String = DefaultRunExecutor.EXECUTOR_ID
private const val DEBUG_EXECUTOR_ID: String = ToolWindowId.DEBUG

/**
 * [SeRunConfigurationsProvider] finds the run configurations by name, and starts the selected one with the executor of the command.
 *
 * A test that starts a run configuration sets the program runners of the application.
 * So the executor does not depend on the installed plugins.
 */
@TestApplication
class SeRunConfigurationsProviderTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project: Project get() = projectFixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  private val configurationType = TestConfigurationType()

  @Test
  fun aPatternFindsTheRunConfigurationsWithAMatchingName() = timeoutRunBlocking {
    addRunConfigurations("SeAlpha", "SeBeta")

    withProvider { provider ->
      assertEquals(listOf("SeAlpha"), provider.search("SeAl").names())
    }
  }

  @Test
  fun aCommandIsNotAPartOfThePattern() = timeoutRunBlocking {
    addRunConfigurations("SeAlpha", "SeBeta")

    withProvider { provider ->
      assertEquals(listOf("SeAlpha"), provider.search("/run SeAl").names())
      assertEquals(listOf("SeBeta"), provider.search("/d SeBe").names(), "a prefix of a command is the command")
      assertEquals(listOf("SeAlpha", "SeBeta"), provider.search("/run ").names(),
                   "a command without a pattern finds all run configurations")
    }
  }

  @Test
  fun aBlankQueryFindsNothing() = timeoutRunBlocking {
    addRunConfigurations("SeAlpha")

    withProvider { provider ->
      assertEquals(emptyList<String>(), provider.search("").names())
      assertEquals(emptyList<String>(), provider.search("  ").names())
    }
  }

  @Test
  fun theExactMatchOfThePatternHasAHigherWeight() = timeoutRunBlocking {
    addRunConfigurations("SeAlpha", "SeAlphabet")

    withProvider { provider ->
      val weights = provider.search("/run SeAlpha").associate { it.name to it.weight() }
      assertEquals(mapOf("SeAlpha" to SeRunConfigurationsProvider.EXACT_MATCH_WEIGHT, "SeAlphabet" to 0), weights)
    }
  }

  @Test
  fun theCommandSelectsTheExecutor() = timeoutRunBlocking {
    setProgramRunners(RUN_EXECUTOR_ID, DEBUG_EXECUTOR_ID)
    val settings = addRunConfigurations("SeAlpha").single()

    withProvider { provider ->
      assertEquals(RUN_EXECUTOR_ID, provider.start(settings, "/run SeAlpha", modifiers = 0))
      assertEquals(RUN_EXECUTOR_ID, provider.start(settings, "/r SeAlpha", modifiers = 0))
      assertEquals(DEBUG_EXECUTOR_ID, provider.start(settings, "/debug SeAlpha", modifiers = InputEvent.SHIFT_DOWN_MASK),
                   "Shift does not change the executor of a command")
      assertEquals(DEBUG_EXECUTOR_ID, provider.start(settings, "/ SeAlpha", modifiers = 0),
                   "a prefix of both commands is the debug command")
    }
  }

  @Test
  fun shiftSelectsTheRunExecutorWithoutACommand() = timeoutRunBlocking {
    setProgramRunners(RUN_EXECUTOR_ID, DEBUG_EXECUTOR_ID)
    val settings = addRunConfigurations("SeAlpha").single()

    withProvider { provider ->
      assertEquals(DEBUG_EXECUTOR_ID, provider.start(settings, "SeAlpha", modifiers = 0))
      assertEquals(RUN_EXECUTOR_ID, provider.start(settings, "SeAlpha", modifiers = InputEvent.SHIFT_DOWN_MASK))
    }
  }

  @Test
  fun theOtherExecutorStartsARunConfigurationThatNoRunnerOfTheCommandCanRun() = timeoutRunBlocking {
    setProgramRunners(RUN_EXECUTOR_ID)
    val settings = addRunConfigurations("SeAlpha").single()

    withProvider { provider ->
      assertEquals(RUN_EXECUTOR_ID, provider.start(settings, "/debug SeAlpha", modifiers = 0))
      assertEquals(runExecutor().actionName, provider.search("/debug SeAlpha").single().description(),
                   "the hint names the executor that starts the run configuration")
    }
  }

  @Test
  fun theProviderStartsARunConfigurationWithTheDataContextOfTheFactory() = timeoutRunBlocking {
    setProgramRunners(RUN_EXECUTOR_ID, DEBUG_EXECUTOR_ID)
    val settings = addRunConfigurations("SeAlpha").single()
    val dataContext = SimpleDataContext.getProjectContext(project)
    val provider = SeRunConfigurationsProviderFactory().getItemsProvider(project, dataContext) as SeRunConfigurationsProvider
    Disposer.register(disposable, provider)

    val item = TestItemWrapper(settings)
    assertTrue(provider.itemSelected(SeRunConfigurationsItem(item, weight = 0, command = null), 0, "SeAlpha"))

    assertSame(dataContext, item.starts.single().second)
  }

  @Test
  fun theHintNamesTheExecutorOfTheTypedCommand() = timeoutRunBlocking {
    setProgramRunners(RUN_EXECUTOR_ID, DEBUG_EXECUTOR_ID)
    addRunConfigurations("SeAlpha")

    withProvider { provider ->
      assertEquals(runExecutor().actionName, provider.search("/run SeAlpha").single().description())
      assertEquals(debugExecutor().actionName, provider.search("/debug SeAlpha").single().description())

      val hint = provider.search("SeAlpha").single().description().orEmpty()
      assertTrue(hint.startsWith(debugExecutor().actionName) && runExecutor().actionName in hint,
                 "without a command, the hint names the executors of Enter and Shift+Enter: $hint")
    }
  }

  @Test
  fun theCommandsAreRunAndDebug() = timeoutRunBlocking {
    withProvider { provider ->
      val commands = provider.getSupportedCommands()

      assertEquals(listOf("run", "debug"), commands.map { it.command })
      assertTrue(commands.all { it.providerId == SeProviderIdUtils.RUN_CONFIGURATIONS_ID }, commands.toString())
    }
  }

  @Test
  fun theNewSearchEverywhereUsesTheProviderAndNoLegacyContributor() = timeoutRunBlocking {
    val initEvent = AnActionEvent.createEvent(SimpleDataContext.getProjectContext(project), null, "", ActionUiKind.NONE, null)
    val holder = SeProvidersHolder.initialize(initEvent, project, SeSessionImpl(UUID.randomUUID().toString()), "Test", true)
    Disposer.register(disposable, holder)

    val providerId = SeProviderIdUtils.RUN_CONFIGURATIONS_ID.toProviderId()
    assertInstanceOf(SeRunConfigurationsProvider::class.java, holder.get(providerId, isAllTab = true)?.provider)
    assertFalse(providerId in holder.legacyContributors.allTab, "the new Search Everywhere must not create the legacy contributor")
  }

  /**
   * Adds the run configurations with [names] to the project. The run configurations go away at the end of the test.
   */
  private suspend fun addRunConfigurations(vararg names: String): List<RunnerAndConfigurationSettings> {
    val runManager = RunManager.getInstance(project)
    val settings = withContext(Dispatchers.EDT) {
      names.map { name -> runManager.createConfiguration(name, configurationType).also { runManager.addConfiguration(it) } }
    }
    Disposer.register(disposable) { settings.forEach { runManager.removeConfiguration(it) } }
    return settings
  }

  private fun setProgramRunners(vararg executorIds: String) {
    ExtensionTestUtil.maskExtensions(ProgramRunner.PROGRAM_RUNNER_EP, listOf(TestProgramRunner(executorIds.toSet())), disposable)
  }

  private suspend fun <T> withProvider(block: suspend (SeRunConfigurationsProvider) -> T): T {
    val provider = SeRunConfigurationsProvider(project, SimpleDataContext.getProjectContext(project))
    try {
      return block(provider)
    }
    finally {
      Disposer.dispose(provider)
    }
  }

  private suspend fun SeRunConfigurationsProvider.search(query: String): List<SeRunConfigurationsItem> {
    val filter = SeEverywhereFilterImpl(isAllTab = true, isEverywhere = false, disabledProviderIds = emptyList())
    val items = mutableListOf<SeRunConfigurationsItem>()
    collectItems(SeParams(query, filter.toState())) { item ->
      items.add(item as SeRunConfigurationsItem)
      true
    }
    return items
  }

  /**
   * Selects [settings] for [searchText] with [modifiers], and returns the id of the executor that starts it.
   */
  private suspend fun SeRunConfigurationsProvider.start(
    settings: RunnerAndConfigurationSettings,
    searchText: String,
    modifiers: Int,
  ): String {
    val item = TestItemWrapper(settings)
    val isClosed = itemSelected(SeRunConfigurationsItem(item, weight = 0, command = null), modifiers, searchText)
    assertTrue(isClosed, "the selection closes the popup")
    return item.starts.single().first.id
  }
}

private fun runExecutor(): Executor = DefaultRunExecutor.getRunExecutorInstance()

private fun debugExecutor(): Executor = ExecutorRegistry.getInstance().getExecutorById(DEBUG_EXECUTOR_ID)!!

private val SeRunConfigurationsItem.name: String
  get() = (item.value as RunnerAndConfigurationSettings).name

private fun List<SeRunConfigurationsItem>.names(): List<String> = map { it.name }

private suspend fun SeRunConfigurationsItem.description(): String? = (presentation() as SeBasicItemPresentationImpl).description

/**
 * Keeps the executor and the data context of each start, and does not start the run configuration.
 */
private class TestItemWrapper(settings: RunnerAndConfigurationSettings)
  : ChooseRunConfigurationPopup.ItemWrapper<RunnerAndConfigurationSettings>(settings) {
  val starts: MutableList<Pair<Executor, DataContext>> = mutableListOf()

  override fun perform(project: Project, executor: Executor, context: DataContext) {
    starts.add(executor to context)
  }

  override fun getIcon(): Icon? = null

  override fun getText(): String = value!!.name
}

/**
 * Can run a [TestRunConfiguration] with the executors of [executorIds].
 */
private class TestProgramRunner(private val executorIds: Set<String>) : ProgramRunner<RunnerSettings> {
  override fun getRunnerId(): String = "SeRunConfigurationsProviderTest"

  override fun canRun(executorId: String, profile: RunProfile): Boolean = profile is TestRunConfiguration && executorId in executorIds

  override fun execute(environment: ExecutionEnvironment) {
    throw UnsupportedOperationException()
  }
}

private class TestConfigurationType : SimpleConfigurationType("SeRunConfigurationsProviderTest", "Search Everywhere test", null,
                                                               NotNullLazyValue.createConstantValue(EmptyIcon.ICON_16)) {
  override fun createTemplateConfiguration(project: Project): RunConfiguration = TestRunConfiguration(project, this)
}

private class TestRunConfiguration(project: Project, type: TestConfigurationType) : RunConfigurationBase<Any>(project, type, null) {
  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = throw UnsupportedOperationException()

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? = null
}
