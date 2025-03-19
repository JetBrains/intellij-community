// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@State(name = "VcsProjectSettings", storages = [Storage("vcs.xml")])
@ApiStatus.Internal
class VcsSharedProjectSettings : BaseState(), PersistentStateComponent<VcsSharedProjectSettings> {
  var isDetectVcsMappingsAutomatically: Boolean by property(true)

  override fun getState(): VcsSharedProjectSettings {
    return this
  }

  override fun loadState(state: VcsSharedProjectSettings) {
    copyFrom(state)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VcsSharedProjectSettings = project.service()
  }
}
