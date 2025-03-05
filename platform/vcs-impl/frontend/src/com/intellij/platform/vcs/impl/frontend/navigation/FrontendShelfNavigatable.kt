// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.navigation

import com.intellij.openapi.project.Project
import com.intellij.pom.NavigatableAdapter
import com.intellij.platform.vcs.impl.frontend.shelf.ShelfService
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FrontendShelfNavigatable(private val project: Project, private val lists: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>) : NavigatableAdapter() {
  override fun navigate(requestFocus: Boolean) {
    ShelfService.getInstance(project).navigateToSource(lists, requestFocus)
  }
}