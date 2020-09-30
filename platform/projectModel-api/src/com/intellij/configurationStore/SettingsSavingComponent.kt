// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

/**
 * Service which implements this interfaces will be asked to [save] custom settings (in their own custom way)
 * when application (for Application level services) or project (for Project level services) is invoked.
 */
interface SettingsSavingComponent {
  // not called in EDT
  suspend fun save()
}

interface SettingsSavingComponentJavaAdapter : SettingsSavingComponent {
  @JvmDefault
  override suspend fun save() {
    doSave()
  }

  fun doSave()
}