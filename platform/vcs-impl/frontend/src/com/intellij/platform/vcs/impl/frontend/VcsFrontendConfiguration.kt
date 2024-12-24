// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = "VcsFrontendConfiguration", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class VcsFrontendConfiguration : PersistentStateComponent<VcsFrontendConfiguration> {
  var shelveDetailsPreviewShown: Boolean = false
  override fun getState(): VcsFrontendConfiguration {
    return this
  }

  override fun loadState(state: VcsFrontendConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {

    @JvmStatic
    fun getInstance(project: Project): VcsFrontendConfiguration = project.service()
  }
}