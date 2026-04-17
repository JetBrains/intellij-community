// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Determines which page of a multipage Vision-based What's New artifact should be shown on IDE startup.
 *
 * Each IDE can implement its own logic to determine the starting page based on project-specific parameters.
 * The page identifier returned by [getId] must exactly match one of the Custom IDs defined in the Vision artifact
 * and be included in the set of allowed IDs returned by [WhatsNewInVisionContentProvider.getAllowedMultipageIds].
 *
 * #### Usage
 * 1. Override this service in your product
 * 2. Implement [getId] to return the appropriate page ID based on your project context
 * 3. Register allowed multipage IDs by overriding [WhatsNewInVisionContentProvider.getAllowedMultipageIds]
 * 4. Configure matching Custom IDs in the Vision website (the identifiers must match exactly)
 * 5. Build the Vision artifact and place it in the target directory
 *
 * #### Expected Behavior
 * - **Single-page artifact**: The only available page is shown, regardless of the ID, [WhatsNewInVisionContentProvider.DEFAULT_MULTIPAGE_ID] logged into statistics
 * - **Multipage artifact without custom provider**: The first page is shown by default, [WhatsNewInVisionContentProvider.DEFAULT_MULTIPAGE_ID] logged into statistics
 *   If the artifact has only one page ID, that page is shown directly.
 * - **Multipage artifact with custom provider**: The page matching the ID from [getId] is shown
 *   - If the returned ID is not in the artifact's multipage IDs, a warning is logged but the ID is still used
 *
 * @see WhatsNewInVisionContentProvider.getAllowedMultipageIds
 * @see <a href="https://youtrack.jetbrains.com/articles/IJPL-A-495/Vision-Based-Whats-New">Vision Based What's new documentation</a>
 */
open class WhatsNewMultipageStartIdProvider(val project: Project) {
  /**
   * Returns the page ID that should be shown on startup for the multipage What's New artifact.
   *
   * The returned ID must exactly match one of the Custom IDs defined in the Vision artifact's `multipageIds` field
   * and be present in [WhatsNewInVisionContentProvider.getAllowedMultipageIds].
   *
   * @return the page ID to display, or `null` to use default behavior
   */
  open suspend fun getId(): String? = WhatsNewInVisionContentProvider.DEFAULT_MULTIPAGE_ID

  companion object {
    fun getInstance(project: Project): WhatsNewMultipageStartIdProvider = project.service()
  }
}
