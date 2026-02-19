// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k1.inspections

import com.intellij.devkit.workspaceModel.inspections.WorkspaceCodeAbsentInspectionBase
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.psi.KtClassOrObject

@VisibleForTesting
@IntellijInternalApi
@ApiStatus.Internal
class WorkspaceCodeAbsentInspection : WorkspaceCodeAbsentInspectionBase() {
  override fun belongToSameModule(ktClass: KtClassOrObject, otherKtClass: KtClassOrObject): Boolean =
    ktClass.moduleInfo == otherKtClass.moduleInfo

  override fun getModuleSearchScope(ktClass: KtClassOrObject): GlobalSearchScope =
    ktClass.moduleInfo.contentScope
}