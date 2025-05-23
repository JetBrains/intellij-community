// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeSessionEntity
import fleet.kernel.DurableRef
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface SeTabFactory {
  fun getTab(scope: CoroutineScope, project: Project?, sessionRef: DurableRef<SeSessionEntity>, initEvent: AnActionEvent): SeTab?

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SeTabFactory> = ExtensionPointName("com.intellij.searchEverywhere.tabFactory")
  }
}