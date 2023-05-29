// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "VcsProjectSettings", storages = [Storage("vcs.xml")])
class VcsSharedProjectSettings : BaseState(), PersistentStateComponent<VcsSharedProjectSettings> {
  var isDetectVcsMappingsAutomatically by property(true)

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
