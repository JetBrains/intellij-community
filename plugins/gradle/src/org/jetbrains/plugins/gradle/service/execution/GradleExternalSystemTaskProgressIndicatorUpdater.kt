// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.impl.FileDownloadEventImpl
import com.intellij.build.events.impl.FileDownloadedEventImpl
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.service.ExternalSystemTaskProgressIndicatorUpdater
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.function.Function

class GradleExternalSystemTaskProgressIndicatorUpdater : ExternalSystemTaskProgressIndicatorUpdater() {

  override fun canUpdate(externalSystemId: ProjectSystemId): Boolean = GradleConstants.SYSTEM_ID == externalSystemId

  override fun getText(description: String,
                       progress: Long,
                       total: Long,
                       unit: String,
                       textWrapper: Function<String, String>): String = textWrapper.apply(description)

  override fun updateIndicator(event: ExternalSystemTaskNotificationEvent,
                               indicator: ProgressIndicator,
                               textWrapper: Function<String, String>) {
    if (event is ExternalSystemBuildEvent && (event.buildEvent is FileDownloadEventImpl || event.buildEvent is FileDownloadedEventImpl)) {
      return
    }
    super.updateIndicator(event, indicator, textWrapper)
  }
}