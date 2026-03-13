// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyKey
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.util.EnumSet
import java.util.concurrent.CancellationException

@Internal
@Experimental
enum class ProjectFrameCapability {
  /**
   * Marks a project as a welcome-experience frame.
   */
  WELCOME_EXPERIENCE,

  /**
   * Suppresses VCS-oriented UI in the frame.
   */
  SUPPRESS_VCS_UI,

  /**
   * Suppresses Project View UI in the frame.
   */
  SUPPRESS_PROJECT_VIEW,

  /**
   * Disables file colors for the frame.
   */
  FORCE_DISABLE_FILE_COLORS,

  /**
   * Suppresses non-essential background activities for the frame.
   */
  SUPPRESS_BACKGROUND_ACTIVITIES,

  /**
   * Suppresses scanning and indexing activities for the frame.
   */
  SUPPRESS_INDEXING_ACTIVITIES,

  /**
   * Excludes the frame from global window traversal order.
   */
  EXCLUDE_FROM_WINDOW_SWITCH_ORDER,

  /**
   * Excludes the frame from project-window traversal order and project-window list entries.
   */
  EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER,
}

/**
 * Optional startup UI policy for a project frame.
 *
 * This policy is consumed during frame/toolwindow initialization to adjust initial focus and
 * visibility (for example, select a specific Project View pane or activate/hide toolwindows).
 */
@Internal
@Experimental
data class ProjectFrameUiPolicy(
  /** Project View pane id to select on startup. */
  val projectPaneToActivateId: String? = null,

  /** Toolwindow id to activate after toolwindow initialization. */
  val startupToolWindowIdToActivate: String? = null,

  /** Toolwindow ids to hide after startup activation. */
  val toolWindowIdsToHideOnStartup: Set<String> = emptySet(),

  /**
   * Toolwindow layout profile id used to seed project frame layout on first open (when no
   * project-specific toolwindow layout has been persisted yet).
   */
  val toolWindowLayoutProfileId: String? = null,
) {
  fun isEmpty(): Boolean {
    return projectPaneToActivateId == null &&
           startupToolWindowIdToActivate == null &&
           toolWindowIdsToHideOnStartup.isEmpty() &&
           toolWindowLayoutProfileId == null
  }
}

private val LOG = logger<ProjectFrameCapabilitiesService>()

@Internal
@Experimental
interface ProjectFrameCapabilitiesProvider {
  /**
   * Returns frame capabilities contributed by this provider for [project].
   */
  fun getCapabilities(project: Project): Set<ProjectFrameCapability>

  /**
   * Returns optional startup UI policy for [project].
   *
   * [capabilities] contains the aggregated capabilities produced by all providers and can be used
   * as an input signal, so providers avoid duplicating project classification predicates.
   */
  fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy?
}

/**
 * Aggregates project frame capabilities and optional startup UI policy from
 * [ProjectFrameCapabilitiesProvider] extensions.
 */
@Service(Service.Level.APP)
@Internal
@Experimental
class ProjectFrameCapabilitiesService {
  companion object {
    @VisibleForTesting
    val EP_NAME: ExtensionPointName<ProjectFrameCapabilitiesProvider> = ExtensionPointName("com.intellij.projectFrameCapabilitiesProvider")

    suspend fun getInstance(): ProjectFrameCapabilitiesService = serviceAsync()

    @Deprecated("Use getInstance instead", ReplaceWith("getInstance()"))
    fun getInstanceSync(): ProjectFrameCapabilitiesService = service()
  }

  private val capabilitiesByProject = NotNullLazyKey.createLazyKey<Set<ProjectFrameCapability>, Project>(
    "project.frame.capabilities"
  ) { project ->
    computeProjectFrameCapabilities(project)
  }

  fun has(project: Project, capability: ProjectFrameCapability): Boolean {
    return getAll(project).contains(capability)
  }

  fun getAll(project: Project): Set<ProjectFrameCapability> {
    return getOrComputeCapabilities(project)
  }

  /**
   * Returns startup UI policy for [project], if any.
   *
   * At most one provider policy is accepted; if multiple policies are contributed,
   * the first one is kept and an error is logged.
   */
  fun getUiPolicy(project: Project): ProjectFrameUiPolicy? {
    if (project.isDisposed) {
      return null
    }

    val providers = EP_NAME.extensionsIfPointIsRegistered
    if (providers.isEmpty()) {
      return null
    }

    val capabilities = getOrComputeCapabilities(project)
    var uiPolicy: ProjectFrameUiPolicy? = null
    var uiPolicyProvider: ProjectFrameCapabilitiesProvider? = null

    forEachProjectFrameCapabilitiesProviderSafe(providers) { provider ->
      val providerUiPolicy = provider.getUiPolicy(project, capabilities)?.takeUnless(ProjectFrameUiPolicy::isEmpty)
      if (providerUiPolicy != null) {
        if (uiPolicy == null) {
          uiPolicy = providerUiPolicy
          uiPolicyProvider = provider
        }
        else {
          LOG.error(
            "Multiple project frame UI policies are provided for project '${project.name}'. " +
            "Only one provider is allowed. Keeping ${uiPolicyProvider?.javaClass?.name}, ignoring ${provider.javaClass.name}."
          )
        }
      }
    }

    return uiPolicy
  }

  private fun getOrComputeCapabilities(project: Project): Set<ProjectFrameCapability> {
    if (project.isDisposed) {
      return emptySet()
    }
    return capabilitiesByProject.getValue(project)
  }
}

@Internal
@Experimental
fun isBackgroundActivitiesSuppressedSync(project: Project?): Boolean {
  if (project == null) {
    return false
  }
  if (project is LightEditCompatible) {
    return true
  }
  return service<ProjectFrameCapabilitiesService>().has(project, ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES)
}

@Internal
@Experimental
suspend fun isBackgroundActivitiesSuppressed(project: Project?): Boolean {
  if (project == null) {
    return false
  }
  if (project is LightEditCompatible) {
    return true
  }
  return serviceAsync<ProjectFrameCapabilitiesService>().has(project, ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES)
}

@Internal
@Experimental
fun isIndexingActivitiesSuppressedSync(project: Project?): Boolean {
  if (project == null) {
    return false
  }
  if (project is LightEditCompatible) {
    return true
  }
  val capabilitiesService = service<ProjectFrameCapabilitiesService>()
  return capabilitiesService.has(project, ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES) ||
         capabilitiesService.has(project, ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES)
}

@Internal
@Experimental
suspend fun isIndexingActivitiesSuppressed(project: Project?): Boolean {
  if (project == null) {
    return false
  }
  if (project is LightEditCompatible) {
    return true
  }
  val capabilitiesService = serviceAsync<ProjectFrameCapabilitiesService>()
  return capabilitiesService.has(project, ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES) ||
         capabilitiesService.has(project, ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES)
}

private fun computeProjectFrameCapabilities(project: Project): Set<ProjectFrameCapability> {
  val providers = ProjectFrameCapabilitiesService.EP_NAME.extensionsIfPointIsRegistered
  if (providers.isEmpty()) {
    return emptySet()
  }

  val capabilities = EnumSet.noneOf(ProjectFrameCapability::class.java)

  forEachProjectFrameCapabilitiesProviderSafe(providers) { provider ->
    capabilities.addAll(provider.getCapabilities(project))
  }

  return capabilities.takeIf { it.isNotEmpty() }?.toSet() ?: emptySet()
}

private inline fun forEachProjectFrameCapabilitiesProviderSafe(
  providers: List<ProjectFrameCapabilitiesProvider>,
  consumer: (ProjectFrameCapabilitiesProvider) -> Unit,
) {
  for (provider in providers) {
    try {
      consumer(provider)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error("Project frame capabilities provider '${provider.javaClass.name}' failed", e)
    }
  }
}
