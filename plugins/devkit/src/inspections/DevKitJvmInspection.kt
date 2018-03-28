// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jvm.inspection.JvmLocalInspection
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase.isAllowed

abstract class DevKitJvmInspection : JvmLocalInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!isAllowed(holder)) return PsiElementVisitor.EMPTY_VISITOR
    return super.buildVisitor(holder, isOnTheFly)
  }
}
