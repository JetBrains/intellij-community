// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.kaModule
import org.jetbrains.kotlin.psi.KtClassOrObject

abstract class WorkspaceInspectionBase : LocalInspectionTool() {
  fun getModuleSearchScope(ktClass: KtClassOrObject): GlobalSearchScope =
    analyze(ktClass) {
      ktClass.kaModule.contentScope
    }
}