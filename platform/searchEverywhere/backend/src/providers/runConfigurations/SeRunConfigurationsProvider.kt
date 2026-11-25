// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.runConfigurations

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ChooseRunConfigurationManager
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.IdeBundle
import com.intellij.ide.SearchTopHitProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.searchEverywhere.SeCommandInfo
import com.intellij.platform.searchEverywhere.SeCommandInfoFactory
import com.intellij.platform.searchEverywhere.SeCommandsProviderInterface
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.psi.codeStyle.NameUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent

/**
 * A run configuration that [SeRunConfigurationsProvider] finds.
 *
 * [command] is the command of the search query, or `null` when the query has no command.
 * The presentation names the executor that the command selects.
 */
@ApiStatus.Internal
class SeRunConfigurationsItem(
  val item: ChooseRunConfigurationPopup.ItemWrapper<*>,
  private val weight: Int,
  val command: SeRunConfigurationsCommand?,
) : SeItem {
  override val rawObject: Any get() = item
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeRunConfigurationsPresentationProvider.getPresentation(item, command)
}

/**
 * A command of [SeRunConfigurationsProvider]. The command selects the executor that starts the run configuration.
 */
@ApiStatus.Internal
enum class SeRunConfigurationsCommand(val command: String) {
  RUN("run"),
  DEBUG("debug");

  val commandWithPrefix: String get() = SearchTopHitProvider.getTopHitAccelerator() + command

  val definition: @Nls String
    get() = when (this) {
      RUN -> IdeBundle.message("searcheverywhere.runconfigurations.command.run.description")
      DEBUG -> IdeBundle.message("searcheverywhere.runconfigurations.command.debug.description")
    }

  /**
   * Returns the executor that starts [settings] for this command.
   * When no runner of this executor can run [settings], the executor of the other command starts it.
   */
  fun findExecutor(settings: RunnerAndConfigurationSettings): Executor? {
    val runExecutor: Executor? = DefaultRunExecutor.getRunExecutorInstance()
    val debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG)

    val executor = (if (this == RUN) runExecutor else debugExecutor) ?: return null
    if (ProgramRunner.getRunner(executor.id, settings.configuration) != null) return executor
    return if (executor == runExecutor) debugExecutor else runExecutor
  }

  companion object {
    /** Without a command, Enter selects the debug command, and Shift+Enter selects the run command. */
    fun fromModifiers(modifiers: Int): SeRunConfigurationsCommand =
      if (modifiers and InputEvent.SHIFT_DOWN_MASK == 0) DEBUG else RUN
  }
}

/**
 * A search query of [SeRunConfigurationsProvider]: the [command] and the [pattern] of the run configuration name.
 */
internal data class SeRunConfigurationsQuery(val command: SeRunConfigurationsCommand?, val pattern: String) {
  companion object {
    /**
     * Splits [query] into the command and the pattern.
     *
     * The first word of [query] is a command when a space follows it and the word is a prefix of `/debug` or `/run`.
     * A prefix of both commands, such as `/`, selects the debug command.
     */
    fun parse(query: String): SeRunConfigurationsQuery {
      if (query.isBlank() || ' ' !in query) return SeRunConfigurationsQuery(null, query)

      val firstWord = query.substringBefore(' ')
      val command = when {
        SeRunConfigurationsCommand.DEBUG.commandWithPrefix.startsWith(firstWord) -> SeRunConfigurationsCommand.DEBUG
        SeRunConfigurationsCommand.RUN.commandWithPrefix.startsWith(firstWord) -> SeRunConfigurationsCommand.RUN
        else -> return SeRunConfigurationsQuery(null, query)
      }
      return SeRunConfigurationsQuery(command, query.substring(firstWord.length + 1))
    }
  }
}

/**
 * Finds the run configurations of [project] by name, and starts the selected one.
 *
 * The commands `/run` and `/debug` select the executor. Without a command, Shift selects the run executor.
 * The provider starts a run configuration with [dataContext], the data context of the factory.
 */
@ApiStatus.Internal
class SeRunConfigurationsProvider(
  private val project: Project,
  private val dataContext: DataContext,
) : SeItemsProvider, SeCommandsProviderInterface {
  override val id: String get() = SeProviderIdUtils.RUN_CONFIGURATIONS_ID
  override val displayName: @Nls String get() = IdeBundle.message("searcheverywhere.run.configs.tab.name")

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    if (params.inputQuery.isBlank()) return

    val query = SeRunConfigurationsQuery.parse(params.inputQuery)
    val matcher = NameUtil.buildMatcher(query.pattern).build()
    for (item in ChooseRunConfigurationManager.createFlatSettingsList(project)) {
      val text = item.text
      if (!matcher.matches(text)) continue

      val weight = if (text == query.pattern) EXACT_MATCH_WEIGHT else 0
      if (!collector.put(SeRunConfigurationsItem(item, weight, query.command))) return
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val runConfigurationItem = (item as? SeRunConfigurationsItem)?.item ?: return false
    val settings = runConfigurationItem.value as? RunnerAndConfigurationSettings ?: return true
    val command = SeRunConfigurationsQuery.parse(searchText).command ?: SeRunConfigurationsCommand.fromModifiers(modifiers)

    withContext(Dispatchers.EDT) {
      val executor = command.findExecutor(settings) ?: return@withContext
      runConfigurationItem.perform(project, executor, dataContext)
    }
    return true
  }

  override suspend fun canBeShownInFindResults(): Boolean = false

  override fun getSupportedCommands(): List<SeCommandInfo> {
    val commandInfoFactory = SeCommandInfoFactory()
    return listOf(SeRunConfigurationsCommand.RUN, SeRunConfigurationsCommand.DEBUG).map {
      commandInfoFactory.create(it.command, it.definition, id)
    }
  }

  override fun dispose() {}

  companion object {
    /** The weight of a run configuration whose text is equal to the pattern. */
    const val EXACT_MATCH_WEIGHT: Int = 2000
  }
}
