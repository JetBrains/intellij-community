// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService

/** Retains IntelliJ Platform metadata in Gradle's cached external project model. */
internal class IntelliJPlatformGradleDataService : AbstractProjectDataService<IntelliJPlatformGradleData, Void>() {
  override fun getTargetDataKey(): Key<IntelliJPlatformGradleData> = IntelliJPlatformGradleData.KEY
}
