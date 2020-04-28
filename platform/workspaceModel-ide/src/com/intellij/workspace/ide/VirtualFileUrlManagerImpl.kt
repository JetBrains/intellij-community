// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.workspace.api.VirtualFileUrlManager

class VirtualFileUrlManagerImpl: VirtualFileUrlManager() {
  companion object {
    fun getInstance(project: Project): VirtualFileUrlManager = project.service<VirtualFileUrlManagerImpl>()
  }
}