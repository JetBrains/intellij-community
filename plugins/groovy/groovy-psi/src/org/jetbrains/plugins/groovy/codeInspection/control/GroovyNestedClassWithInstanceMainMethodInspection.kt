// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.control

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiModifier
import com.siyeh.ig.psiutils.ClassUtils
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionTool
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.GroovyMainMethodSearcher

class GroovyNestedClassWithInstanceMainMethodInspection: GroovyLocalInspectionTool() {
  override fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor {
    return object : GroovyElementVisitor() {
      override fun visitMethod(method: GrMethod) {
        super.visitMethod(method)
        if (!GroovyConfigUtils.isAtLeastGroovy50(method) || !isInstanceMainMethod(method)) return
        val clazz = method.containingClass ?: return
        val outerMostClass = ClassUtils.getOutermostContainingClass(clazz) ?: return
        if (outerMostClass == clazz) return

        holder.registerProblem(method.nameIdentifierGroovy, GroovyBundle.message("instance.main.method.used.inside.nested.class"))
      }
    }
  }

  private fun isInstanceMainMethod(method: GrMethod): Boolean {
    return method.name == "main"
           && !method.hasModifierProperty(PsiModifier.STATIC)
           && GroovyMainMethodSearcher.isMainMethod(method)
  }
}