// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jvm.inspection.JvmLocalInspection
import com.intellij.psi.PsiElementVisitor

/**
 * Base class for JVM API (declaration-based) DevKit inspections.
 *
 * Override [isAllowed] to add additional constraints (e.g., required class in scope via [DevKitInspectionUtil.isClassAvailable])
 * to skip running inspection completely whenever possible.
 *
 * @see DevKitInspectionUtil
 * @see DevKitUastInspectionBase
 */
abstract class DevKitJvmInspection : JvmLocalInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (isAllowed(holder)) return super.buildVisitor(holder, isOnTheFly)

    return PsiElementVisitor.EMPTY_VISITOR
  }

  protected open fun isAllowed(holder: ProblemsHolder) = DevKitInspectionUtil.isAllowed(holder.file)
}
