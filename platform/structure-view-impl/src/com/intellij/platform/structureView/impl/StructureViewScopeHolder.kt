// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class StructureViewScopeHolder(val cs: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): StructureViewScopeHolder = project.service<StructureViewScopeHolder>()
  }
}