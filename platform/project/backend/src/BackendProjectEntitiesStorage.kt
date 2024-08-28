// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project.backend

import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectEntitiesStorage
import com.intellij.platform.project.asEntityOrNull
import fleet.kernel.change
import fleet.kernel.shared

internal class BackendProjectEntitiesStorage : ProjectEntitiesStorage() {
  override suspend fun removeProjectEntity(project: Project): Unit = withKernel {
    change {
      shared {
        project.asEntityOrNull()?.delete()
      }
    }
  }
}
