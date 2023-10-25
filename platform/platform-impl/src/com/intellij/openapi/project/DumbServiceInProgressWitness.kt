// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.platform.backend.observation.ActivityInProgressWitness
import kotlinx.coroutines.suspendCancellableCoroutine

class DumbServiceInProgressWitness : ActivityInProgressWitness {

  override val presentableName: String = "dumb-mode"

  override suspend fun isInProgress(project: Project): Boolean = DumbService.isDumb(project)

  override suspend fun awaitConfiguration(project: Project) {
    suspendCancellableCoroutine {
      DumbService.getInstance(project).runWhenSmart {
        it.resumeWith(Result.success(Unit))
      }
    }
  }
}