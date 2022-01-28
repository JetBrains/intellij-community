// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.notification

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.project.Project
import java.io.Serializable

data class ExternalAnnotationsTaskId(val targetDataKey: Key<*>, val projectId: String) : Serializable {
  companion object {
    fun of(targetDataKey: Key<*>, project: Project): ExternalAnnotationsTaskId {
      val projectId = if (project.isDisposed) project.name else "${project.name}:${project.locationHash}"
      return ExternalAnnotationsTaskId(targetDataKey, projectId)
    }
  }
}