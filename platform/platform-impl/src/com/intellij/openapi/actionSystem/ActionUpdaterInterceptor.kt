// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.Project

interface ActionUpdaterInterceptor {

  fun allowsFastUpdate(project: Project?, group: ActionGroup, place: String): Boolean = true

  suspend fun expandActionGroup(presentationFactory: PresentationFactory,
                                context: DataContext,
                                place: String,
                                group: ActionGroup,
                                isToolbarAction: Boolean,
                                original: suspend () -> List<AnAction>): List<AnAction> =
    original()

  suspend fun rearrangeByPromoters(actions: List<AnAction>,
                                   context: DataContext,
                                   original: suspend () -> List<AnAction>): List<AnAction> =
    original()
}
