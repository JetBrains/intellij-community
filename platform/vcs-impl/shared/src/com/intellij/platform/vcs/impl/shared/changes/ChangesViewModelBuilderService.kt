// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

/**
 * Allows providing partial support for specific node types for [TreeModelBuilder] in RD mode
 */
@ApiStatus.Internal
interface ChangesViewModelBuilderService {
  fun TreeModelBuilder.setChangeList(
    changeLists: Collection<ChangeList>,
    skipSingleDefaultChangeList: Boolean,
    changeDecoratorProvider: Function<in ChangeNodeDecorator, out ChangeNodeDecorator>?,
  )

  fun TreeModelBuilder.createNodes()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ChangesViewModelBuilderService = project.service()
  }
}