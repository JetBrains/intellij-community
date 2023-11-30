// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.feedback

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

/**
 * This class provides feedback dialogs.
 *
 * This class acts like an interface and overridden in `intellij.platform.feedback` module.
 * It helps to get around the problem of circular dependency between current module and `intellij.platform.feedback` module.
 *
 * @see com.intellij.platform.feedback.impl.PlatformFeedbackDialogsImpl
 */
open class PlatformFeedbackDialogs {

  companion object {
    @JvmStatic
    fun getInstance(): PlatformFeedbackDialogs = service()
  }

  /**
   * This method creates a new feedback form for general feedback located in `Help -> Submit Feedback...`.
   */
  open fun createGeneralFeedbackDialog(project: Project?): DialogWrapper? {
    return null
  }

  /**
   * This method creates a new feedback form for evaluative feedback that will be shown to trial users.
   */
  open fun createEvaluationFeedbackDialog(project: Project?): DialogWrapper? {
    return null
  }

  /**
   * This method creates a new feedback form for feedback on plugin uninstallation, located on the plugin card in the settings.
   */
  open fun getUninstallFeedbackDialog(pluginId: String, pluginName: String, project: Project?): DialogWrapper? {
    return null
  }

  /**
   * This method creates a new feedback form for plugin disabling feedback, located on the plugin card in the settings.
   */
  open fun getDisableFeedbackDialog(pluginId: String, pluginName: String, project: Project?): DialogWrapper? {
    return null
  }
}