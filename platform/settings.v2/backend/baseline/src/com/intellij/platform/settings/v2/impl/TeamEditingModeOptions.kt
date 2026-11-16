// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../../spec/layer-locality.spec.md

import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.settings.v2.EDIT_TEAM_LAYER
import com.intellij.platform.settings.v2.SettingsContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls

/** Holds the id of the team-editing mode providers. */
private const val TEAM_EDITING_MODE_PROVIDER_ID: String = "settings.v2"

/**
 * Lists the option of Go to Action and Search Everywhere that toggles the team-editing mode ([EDIT_TEAM_LAYER]) at
 * the application context. It is registered at the `com.intellij.search.topHitProvider` extension point.
 */
internal class ApplicationTeamEditingModeOptionProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  override fun getId(): String = TEAM_EDITING_MODE_PROVIDER_ID

  override fun getOptions(): Collection<OptionDescription> =
    listOf(teamEditingModeOption(SettingsV2Bundle.message("search.option.team.editing.mode.application"), SettingsContext.app()))
}

/**
 * Lists the option of Go to Action and Search Everywhere that toggles the team-editing mode ([EDIT_TEAM_LAYER]) at
 * the project context. It is registered at the `com.intellij.search.projectOptionsTopHitProvider` extension point.
 */
internal class ProjectTeamEditingModeOptionProvider : OptionsSearchTopHitProvider.ProjectLevelProvider {
  override fun getId(): String = TEAM_EDITING_MODE_PROVIDER_ID

  override fun getOptions(project: Project): Collection<OptionDescription> =
    listOf(teamEditingModeOption(SettingsV2Bundle.message("search.option.team.editing.mode.project"), SettingsContext.project(project)))
}

/**
 * Creates the boolean option that shows and toggles [EDIT_TEAM_LAYER] at [context]. It has no configurable.
 *
 * An option sets its state with a plain call on the EDT, but a write suspends (ADR 0032). So the option launches the
 * write and reports the requested mode until the write ends. The old Search Everywhere popup reads the state right
 * after the call (ADR 0173).
 */
private fun teamEditingModeOption(label: @Nls String, context: SettingsContext): BooleanOptionDescription =
  object : BooleanOptionDescription(label, null) {
    @Volatile
    private var requestedMode: Boolean? = null

    override fun isOptionEnabled(): Boolean = requestedMode ?: EDIT_TEAM_LAYER[context]

    override fun setOptionState(enabled: Boolean) {
      requestedMode = enabled
      service<TeamEditingModeWriteScope>().coroutineScope.launch {
        try {
          EDIT_TEAM_LAYER.set(context, enabled)
        }
        finally {
          requestedMode = null
        }
      }
    }
  }

/** Holds the coroutine scope of the writes that [teamEditingModeOption] launches. */
@Service(Service.Level.APP)
private class TeamEditingModeWriteScope(val coroutineScope: CoroutineScope)
