// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsActions
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
@Volatile
var debugUpdateMode: UpdateMode? = null

@ApiStatus.Internal
enum class UpdateMode {
  /**
   * No patches, update from the IJ site
   */
  MANUAL_DOWNLOAD,

  /**
   * A patch is available and will be applied automatically after the IDE restarts
   */
  PATCH,

  /**
   * A patch is available but must be applied manually because the IDE does not support restarting automatically
   */
  PATCH_MANUAL
}

@ApiStatus.Internal
fun getUpdateMode(platformUpdate: PlatformUpdates.Loaded, testPatch: Path?): UpdateMode? {
  val calculatedMode = when {
    platformUpdate.patches != null || testPatch != null -> {
      if (ApplicationManager.getApplication().isRestartCapable()) UpdateMode.PATCH
      else UpdateMode.PATCH_MANUAL
    }
    platformUpdate.newBuild.downloadUrl != null -> UpdateMode.MANUAL_DOWNLOAD

    else -> null
  }

  return debugUpdateMode ?: calculatedMode
}

@ApiStatus.Internal
fun UpdateMode.actionText(): @NlsActions.ActionText String {
  return when (this) {
    UpdateMode.MANUAL_DOWNLOAD -> IdeBundle.message("updates.download.manual.button")
    UpdateMode.PATCH,
    UpdateMode.PATCH_MANUAL,
      -> IdeBundle.message("updates.download.patch.button")
  }
}
