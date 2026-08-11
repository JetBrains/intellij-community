// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.impl.scope

import com.intellij.ide.scopeView.ScopeViewTreeModel
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.impl.DefaultTreeStructurePsiExtractor
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class ScopeTreeStructurePsiExtractor(
  project: Project,
  private val treeModel: AtomicReference<ScopeViewTreeModel?>,
) : DefaultTreeStructurePsiExtractor(project) {
  override fun extractValueFromLegacyUserObject(userObject: Any?): Any? {
    val model = treeModel.load() ?: return null
    return model.getContent(userObject)
  }
}
