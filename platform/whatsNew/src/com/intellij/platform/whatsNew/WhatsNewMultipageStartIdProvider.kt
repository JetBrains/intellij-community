// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * Determines which page of a multipage Vision-based What's New artifact should be shown on IDE startup.
 *
 * Each IDE can implement its own logic to determine the starting page based on project-specific parameters.
 * The page identifier returned by [getId] must exactly match one of the Custom IDs defined in the Vision artifact.
 *
 * #### Usage
 * 1. Override this service in your product
 * 2. Implement [getId] to return the appropriate page ID based on your project context
 * 3. Configure matching Custom IDs in the Vision website (the identifiers must match exactly)
 * 4. Build the Vision artifact and place it in the target directory
 *
 * #### Expected Behavior
 * - **Single-page artifact**: The only available page is shown, regardless of the ID
 * - **Multipage artifact without custom provider**: The first page is shown by default (with a warning logged)
 * - **Multipage artifact with custom provider**: The page matching the ID from [getId] is shown
 *   - If identifiers don't match, the page will not open
 *
 * @see <a href="https://youtrack.jetbrains.com/articles/IJPL-A-495/Vision-Based-Whats-New">Vision Based What's new documentation</a>
 */
open class WhatsNewMultipageStartIdProvider(val project: Project) {
  /**
   * Returns the page ID that should be shown on startup for the multipage What's New artifact.
   *
   * The returned ID must exactly match one of the Custom IDs defined in the Vision artifact's `multipageIds` field.
   * If `null` is returned for a multipage artifact, the first page will be shown with a warning logged.
   *
   * @return the page ID to display, or `null` to use default behavior
   */
  protected open suspend fun getId(): String? = null

  internal suspend fun getIdIfSupported(multipageIds: List<String>): String? {
    if (multipageIds.size == 1) return multipageIds.first()
    val startPageId = getId()
    if (startPageId == null) {
      logger.warn("What's new artifact contains several page ids but getId() returns null. Implement WhatsNewMultipageStartIdProvider.getId() to return one of them. Now trying to show the first page from available.")
      return multipageIds.firstOrNull()
    }
    return startPageId.checkSupported(multipageIds)
  }

  private fun String.checkSupported(multipageIds: List<String>): String {
    if (multipageIds.isEmpty() || this in multipageIds) return this
    else {
      logger.warn("What's new multipage id \"$this\" is not supported. Supported ids: ${multipageIds.joinToString()}")
      return this
    }
  }

  companion object {
    fun getInstance(project: Project): WhatsNewMultipageStartIdProvider = project.service()
  }
}

private val logger = logger<WhatsNewMultipageStartIdProvider>()



