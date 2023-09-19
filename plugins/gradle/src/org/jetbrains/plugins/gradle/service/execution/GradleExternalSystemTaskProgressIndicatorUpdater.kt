// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.ExternalSystemTaskProgressIndicatorUpdater
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.function.Function

class GradleExternalSystemTaskProgressIndicatorUpdater : ExternalSystemTaskProgressIndicatorUpdater() {

  override fun canUpdate(externalSystemId: ProjectSystemId): Boolean = GradleConstants.SYSTEM_ID == externalSystemId

  override fun getText(description: String,
                       progress: Long,
                       total: Long,
                       unit: String,
                       textWrapper: Function<String, String>): String = textWrapper.apply(description)
}