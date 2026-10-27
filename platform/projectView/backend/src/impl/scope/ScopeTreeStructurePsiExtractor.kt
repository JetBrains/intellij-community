// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.impl.DefaultTreeStructurePsiExtractor

internal class ScopeTreeStructurePsiExtractor(
  project: Project,
  private val sharedModel: SharedScopeViewTreeModel,
) : DefaultTreeStructurePsiExtractor(project) {
  override fun extractValueFromLegacyUserObject(userObject: Any?): Any? = sharedModel.getContent(userObject)
}
