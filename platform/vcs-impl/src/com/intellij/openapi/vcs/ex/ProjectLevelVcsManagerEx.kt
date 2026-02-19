// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsConfiguration.StandardConfirmation
import com.intellij.openapi.vcs.VcsConfiguration.StandardOption
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsShowConfirmationOption
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsShowSettingOption
import com.intellij.openapi.vcs.update.ActionInfo
import com.intellij.openapi.vcs.update.UpdateInfoTree
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.Nls

abstract class ProjectLevelVcsManagerEx : ProjectLevelVcsManager() {
  abstract val allOptions: List<PersistentVcsShowSettingOption>

  abstract fun getOptions(option: StandardOption): PersistentVcsShowSettingOption

  abstract val allConfirmations: List<PersistentVcsShowConfirmationOption>

  abstract fun getConfirmation(option: StandardConfirmation): PersistentVcsShowConfirmationOption

  @Deprecated("A plugin should not need to call this.")
  abstract fun notifyDirectoryMappingChanged()

  @Deprecated("Implementation detail")
  abstract val contentManager: ContentManager?

  @RequiresEdt
  abstract fun showUpdateProjectInfo(
    updatedFiles: UpdatedFiles?,
    @Nls displayActionName: @Nls String?,
    actionInfo: ActionInfo?,
    canceled: Boolean,
  ): UpdateInfoTree?

  abstract fun scheduleMappedRootsUpdate()

  @Deprecated("A plugin should not need to call this.")
  abstract fun fireDirectoryMappingsChanged()

  /**
   * @return [com.intellij.openapi.vcs.AbstractVcs.getName] for &lt;Project&gt; mapping if configured;
   * empty string for &lt;None&gt; &lt;Project&gt; mapping;
   * null if no default mapping is configured.
   */
  abstract fun haveDefaultMapping(): String?

  companion object {
    @JvmField
    internal val MAPPING_DETECTION_LOG: Logger = Logger.getInstance(NewMappings::class.java)

    @JvmField
    @Topic.ProjectLevel
    val VCS_ACTIVATED: Topic<VcsActivationListener> = Topic(VcsActivationListener::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    fun getInstanceEx(project: Project): ProjectLevelVcsManagerEx {
      return getInstance(project) as ProjectLevelVcsManagerEx
    }
  }
}
