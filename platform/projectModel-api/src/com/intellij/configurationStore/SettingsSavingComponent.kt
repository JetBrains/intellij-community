// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.progress.blockingContext
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Services which implement this interface will be asked to [save] custom settings (in their own custom way)
 * when application (for application level services) or project (for project level services) settings save is called.
 */
@Internal
interface SettingsSavingComponent {
  suspend fun save()
}

interface SettingsSavingComponentJavaAdapter : SettingsSavingComponent {
  override suspend fun save() {
    blockingContext {
      doSave()
    }
  }

  fun doSave()
}
