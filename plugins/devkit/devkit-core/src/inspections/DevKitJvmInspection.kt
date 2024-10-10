// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.inspection.JvmLocalInspection
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor

/**
 * Base class for JVM API (declaration-based) DevKit inspections.
 * Use [ForClass] when only inspecting classes.
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

  final override fun isDumbAware(): Boolean {
    return false
  }

  abstract class ForClass : DevKitJvmInspection() {

    final override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean?>? {
      return object : DefaultJvmElementVisitor<Boolean> {
        override fun visitClass(clazz: JvmClass): Boolean {
          val sourceElement = clazz.sourceElement
          if (sourceElement !is PsiClass) return true
          checkClass(project, sourceElement, sink)
          return true
        }
      }
    }

    protected abstract fun checkClass(project: Project, psiClass: PsiClass, sink: HighlightSink)
  }
}
