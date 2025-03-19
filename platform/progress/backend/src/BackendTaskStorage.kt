// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.progress.backend

import com.intellij.platform.ide.progress.TaskInfoEntity
import com.intellij.platform.ide.progress.TaskStorage
import com.jetbrains.rhizomedb.ChangeScope
import fleet.kernel.change
import fleet.kernel.delete
import fleet.kernel.shared
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendTaskStorage : TaskStorage() {
  override suspend fun createTaskInfoEntity(provider: ChangeScope.() -> TaskInfoEntity?): TaskInfoEntity? {
    return change {
      shared {
        provider()
      }
    }
  }

  override suspend fun removeTaskInfoEntity(taskInfoEntity: TaskInfoEntity) {
    change {
      shared {
        delete(taskInfoEntity)
      }
    }
  }

  override suspend fun updateTaskInfoEntity(updater: ChangeScope.() -> Unit) {
    change {
      shared {
        updater()
      }
    }
  }
}
