// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import com.intellij.agent.workbench.prompt.core.withGroup
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.AnimatedIcon
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal sealed interface AgentPromptGenerationModelCatalogState {
  data object Loading : AgentPromptGenerationModelCatalogState

  data class Loaded(@JvmField val models: List<AgentPromptGenerationModel>) : AgentPromptGenerationModelCatalogState

  data class Refreshing(@JvmField val models: List<AgentPromptGenerationModel>) : AgentPromptGenerationModelCatalogState

  data object Failed : AgentPromptGenerationModelCatalogState

  data class RefreshFailed(@JvmField val models: List<AgentPromptGenerationModel>) : AgentPromptGenerationModelCatalogState
}

internal fun AgentPromptGenerationModelCatalogState.modelsOrNull(): List<AgentPromptGenerationModel>? {
  return when (this) {
    is AgentPromptGenerationModelCatalogState.Loaded -> models
    is AgentPromptGenerationModelCatalogState.Refreshing -> models
    is AgentPromptGenerationModelCatalogState.RefreshFailed -> models
    AgentPromptGenerationModelCatalogState.Loading,
    AgentPromptGenerationModelCatalogState.Failed,
      -> null
  }
}

internal sealed interface AgentPromptGenerationModelSelectorEntry {
  val displayName: @NlsSafe String

  data class Model(
    @JvmField val modelId: String?,
    override val displayName: @NlsSafe String,
    @JvmField val separatorGroup: AgentPromptGenerationModelGroup? = null,
  ) : AgentPromptGenerationModelSelectorEntry

  data class Status(
    override val displayName: @Nls String,
    @JvmField val kind: Kind,
  ) : AgentPromptGenerationModelSelectorEntry {
    enum class Kind {
      LOADING,
      REFRESHING,
      EMPTY,
      LOAD_FAILED,
      REFRESH_FAILED,
    }
  }

  data class Retry(
    override val displayName: @Nls String,
    @JvmField val providerId: String,
  ) : AgentPromptGenerationModelSelectorEntry
}

internal fun modelCatalogStatusIcon(kind: AgentPromptGenerationModelSelectorEntry.Status.Kind): Icon? {
  return when (kind) {
    AgentPromptGenerationModelSelectorEntry.Status.Kind.LOADING,
    AgentPromptGenerationModelSelectorEntry.Status.Kind.REFRESHING,
      -> AnimatedIcon.Default.INSTANCE
    AgentPromptGenerationModelSelectorEntry.Status.Kind.EMPTY,
    AgentPromptGenerationModelSelectorEntry.Status.Kind.LOAD_FAILED,
    AgentPromptGenerationModelSelectorEntry.Status.Kind.REFRESH_FAILED,
      -> null
  }
}

internal fun buildGenerationModelSelectorEntries(
  providerId: String,
  catalogState: AgentPromptGenerationModelCatalogState?,
  selectedModelId: String?,
  displayNameForSavedModel: (String) -> @NlsSafe String = ::unknownGenerationModelDisplayName,
): List<AgentPromptGenerationModelSelectorEntry> {
  return buildList {
    add(AgentPromptGenerationModelSelectorEntry.Model(null, AgentPromptBundle.message("popup.generation.model.popup.auto")))
    when (catalogState) {
      is AgentPromptGenerationModelCatalogState.Loaded -> {
        addExplicitModelEntries(catalogState.models, selectedModelId, displayNameForSavedModel)
      }
      is AgentPromptGenerationModelCatalogState.Refreshing -> {
        addExplicitModelEntries(catalogState.models, selectedModelId, displayNameForSavedModel)
        add(AgentPromptGenerationModelSelectorEntry.Status(
          AgentPromptBundle.message("popup.generation.model.refreshing"),
          AgentPromptGenerationModelSelectorEntry.Status.Kind.REFRESHING,
        ))
      }
      is AgentPromptGenerationModelCatalogState.RefreshFailed -> {
        addExplicitModelEntries(catalogState.models, selectedModelId, displayNameForSavedModel)
        add(AgentPromptGenerationModelSelectorEntry.Status(
          AgentPromptBundle.message("popup.generation.model.refresh.failed"),
          AgentPromptGenerationModelSelectorEntry.Status.Kind.REFRESH_FAILED,
        ))
        add(AgentPromptGenerationModelSelectorEntry.Retry(AgentPromptBundle.message("popup.generation.model.retry"), providerId))
      }
      AgentPromptGenerationModelCatalogState.Loading -> {
        addSavedUnknownModelEntry(selectedModelId, emptyList(), displayNameForSavedModel)
        add(AgentPromptGenerationModelSelectorEntry.Status(
          AgentPromptBundle.message("popup.generation.model.loading"),
          AgentPromptGenerationModelSelectorEntry.Status.Kind.LOADING,
        ))
      }
      AgentPromptGenerationModelCatalogState.Failed -> {
        addSavedUnknownModelEntry(selectedModelId, emptyList(), displayNameForSavedModel)
        add(AgentPromptGenerationModelSelectorEntry.Status(
          AgentPromptBundle.message("popup.generation.model.load.failed"),
          AgentPromptGenerationModelSelectorEntry.Status.Kind.LOAD_FAILED,
        ))
        add(AgentPromptGenerationModelSelectorEntry.Retry(AgentPromptBundle.message("popup.generation.model.retry"), providerId))
      }
      null -> {
        addSavedUnknownModelEntry(selectedModelId, emptyList(), displayNameForSavedModel)
      }
    }
  }
}

private fun MutableList<AgentPromptGenerationModelSelectorEntry>.addExplicitModelEntries(
  models: List<AgentPromptGenerationModel>,
  selectedModelId: String?,
  displayNameForSavedModel: (String) -> @NlsSafe String,
) {
  val explicitModels = ArrayList<AgentPromptGenerationModel>()
  addSavedUnknownModel(selectedModelId, models, displayNameForSavedModel)?.let(explicitModels::add)
  explicitModels += models
  if (explicitModels.isEmpty()) {
    add(AgentPromptGenerationModelSelectorEntry.Status(
      AgentPromptBundle.message("popup.generation.model.empty"),
      AgentPromptGenerationModelSelectorEntry.Status.Kind.EMPTY,
    ))
    return
  }
  explicitModels.groupedForModelSelector().forEach { section ->
    section.models.forEachIndexed { index, model ->
      add(AgentPromptGenerationModelSelectorEntry.Model(
        modelId = model.id,
        displayName = model.displayName,
        separatorGroup = section.group.takeIf { index == 0 },
      ))
    }
  }
}

private fun MutableList<AgentPromptGenerationModelSelectorEntry>.addSavedUnknownModelEntry(
  selectedModelId: String?,
  models: List<AgentPromptGenerationModel>,
  displayNameForSavedModel: (String) -> @NlsSafe String,
) {
  addSavedUnknownModel(selectedModelId, models, displayNameForSavedModel)?.let { model ->
    add(AgentPromptGenerationModelSelectorEntry.Model(
      modelId = model.id,
      displayName = model.displayName,
      separatorGroup = model.group,
    ))
  }
}

private fun addSavedUnknownModel(
  selectedModelId: String?,
  models: List<AgentPromptGenerationModel>,
  displayNameForSavedModel: (String) -> @NlsSafe String,
): AgentPromptGenerationModel? {
  return selectedModelId
    ?.takeIf { modelId -> models.none { model -> model.id == modelId } }
    ?.let { modelId ->
      AgentPromptGenerationModel(modelId,
                                 displayNameForSavedModel(modelId)).withGroup(AgentPromptGenerationModelGroup.OTHER)
    }
}

internal fun unknownGenerationModelDisplayName(modelId: String): @NlsSafe String {
  val trimmed = modelId.trim()
  if (trimmed.length <= UNKNOWN_MODEL_DISPLAY_NAME_MAX_LENGTH) {
    return trimmed
  }
  return trimmed.take(UNKNOWN_MODEL_DISPLAY_NAME_MAX_LENGTH - ELLIPSIS.length) + ELLIPSIS
}

private const val UNKNOWN_MODEL_DISPLAY_NAME_MAX_LENGTH: Int = 80
private const val ELLIPSIS: String = "..."
