// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly

internal typealias AgentPromptLaunchProfileEditorOpenDialog = ((() -> Unit)?) -> Unit

internal class AgentPromptLaunchProfileEditorRequest(
  @JvmField val project: Project,
  @JvmField val profiles: List<AgentPromptLaunchProfile>,
  @JvmField val activeProfileId: String?,
  @JvmField val defaultProfileId: String?,
  @JvmField val builtInProfiles: List<AgentPromptLaunchProfile>,
  @JvmField val providerEntries: List<ProviderEntry>,
  @JvmField val modelCatalogProvider: (String) -> List<AgentPromptGenerationModel>?,
  @JvmField val modelCatalogStateProvider: (String) -> AgentPromptGenerationModelCatalogState? = { providerId ->
    modelCatalogProvider(providerId)?.let(AgentPromptGenerationModelCatalogState::Loaded)
  },
  @JvmField val requestModelCatalogRefresh: (String, () -> Unit) -> Unit = { _, _ -> },
  @JvmField val newUserProfileId: () -> String,
  @JvmField val onCreateProfile: (AgentPromptLaunchProfile) -> Unit,
  @JvmField val onUpdateProfile: (AgentPromptLaunchProfile) -> Unit,
  @JvmField val onDeleteProfile: (AgentPromptLaunchProfile) -> Boolean,
  @JvmField val onSetDefaultProfile: (AgentPromptLaunchProfile) -> Unit,
)

@Service(Service.Level.APP)
internal class AgentPromptLaunchProfileEditorWindowService {
  private var activeDialog: AgentPromptLaunchProfileEditorDialog? = null
  private var activeRequest: AgentPromptLaunchProfileEditorRequest? = null
  private var restorePromptOnClose: (() -> Unit)? = null

  @RequiresEdt
  fun openOrFocus(
    request: AgentPromptLaunchProfileEditorRequest,
    restorePromptOnClose: (() -> Unit)? = null,
  ) {
    openOrFocus(
      request = request,
      restorePromptOnClose = restorePromptOnClose,
      showDialog = { dialog -> dialog.show() },
      focusDialog = { dialog -> dialog.showOrFocus() },
    )
  }

  @RequiresEdt
  private fun openOrFocus(
    request: AgentPromptLaunchProfileEditorRequest,
    restorePromptOnClose: (() -> Unit)?,
    showDialog: (AgentPromptLaunchProfileEditorDialog) -> Unit,
    focusDialog: (AgentPromptLaunchProfileEditorDialog) -> Unit,
  ) {
    activeRequest = request
    if (restorePromptOnClose != null) {
      this.restorePromptOnClose = restorePromptOnClose
    }

    val existingDialog = activeDialog?.takeUnless { dialog -> dialog.isDisposed }
    if (existingDialog != null) {
      refresh(existingDialog, request)
      focusDialog(existingDialog)
      return
    }

    lateinit var dialog: AgentPromptLaunchProfileEditorDialog
    dialog = AgentPromptLaunchProfileEditorDialog(
      project = request.project,
      profiles = request.profiles,
      activeProfileId = request.activeProfileId,
      defaultProfileId = request.defaultProfileId,
      builtInProfiles = request.builtInProfiles,
      providerEntries = request.providerEntries,
      modelCatalogProvider = { providerId -> currentRequest().modelCatalogProvider(providerId) },
      modelCatalogStateProvider = { providerId -> currentRequest().modelCatalogStateProvider(providerId) },
      requestModelCatalogRefresh = { providerId, onStateChanged ->
        currentRequest().requestModelCatalogRefresh(providerId, onStateChanged)
      },
      newUserProfileId = { currentRequest().newUserProfileId() },
      onCreateProfile = { profile -> currentRequest().onCreateProfile(profile) },
      onUpdateProfile = { profile -> currentRequest().onUpdateProfile(profile) },
      onDeleteProfile = { profile -> currentRequest().onDeleteProfile(profile) },
      onSetDefaultProfile = { profile -> currentRequest().onSetDefaultProfile(profile) },
      onSelectProfile = {},
      onDispose = { handleDisposed(dialog) },
    )
    activeDialog = dialog
    showDialog(dialog)
  }

  private fun currentRequest(): AgentPromptLaunchProfileEditorRequest {
    return checkNotNull(activeRequest)
  }

  private fun refresh(dialog: AgentPromptLaunchProfileEditorDialog, request: AgentPromptLaunchProfileEditorRequest) {
    dialog.refreshProfilesIfUnmodified(
      profiles = request.profiles,
      activeProfileId = request.activeProfileId,
      defaultProfileId = request.defaultProfileId,
      builtInProfiles = request.builtInProfiles,
      providerEntries = request.providerEntries,
    )
  }

  private fun handleDisposed(dialog: AgentPromptLaunchProfileEditorDialog) {
    if (activeDialog !== dialog) {
      return
    }
    activeDialog = null
    activeRequest = null
    val restorePrompt = restorePromptOnClose
    restorePromptOnClose = null
    restorePrompt?.invoke()
  }

  @TestOnly
  @RequiresEdt
  internal fun openOrFocusForTest(
    request: AgentPromptLaunchProfileEditorRequest,
    restorePromptOnClose: (() -> Unit)? = null,
  ) {
    openOrFocus(
      request = request,
      restorePromptOnClose = restorePromptOnClose,
      showDialog = {},
      focusDialog = {},
    )
  }

  @TestOnly
  internal fun activeDialogForTest(): AgentPromptLaunchProfileEditorDialog? {
    return activeDialog
  }

  @TestOnly
  @RequiresEdt
  internal fun clearForTest() {
    val dialog = activeDialog
    activeDialog = null
    activeRequest = null
    restorePromptOnClose = null
    dialog?.disposeIfNeeded()
  }
}
