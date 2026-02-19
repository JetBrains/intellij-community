// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.control

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionTool
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.GroovyMainMethodSearcher

class GroovyMultipleMainMethodsInspection: GroovyLocalInspectionTool() {
  override fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor {
    return object : GroovyElementVisitor() {
      override fun visitMethod(method: GrMethod) {
        if (!GroovyConfigUtils.isAtLeastGroovy50(method) || !isMainMethod(method)) return
        val file = method.containingFile
        val clazz = method.containingClass ?: return

        if (clazz.containingClass != null || getMainMethodCount(clazz) <= 1) return

        if (file is GroovyFileBase && file.isScript) {
          holder.registerProblem(method.nameIdentifierGroovy, GroovyBundle.message("another.main.method.defined.in.the.file"))
        }
        else {
          holder.registerProblem(method.nameIdentifierGroovy, GroovyBundle.message("another.main.method.defined.in.the.class", clazz.name))
        }
      }
    }
  }

  private fun isMainMethod(method: GrMethod): Boolean = method.name == "main" && GroovyMainMethodSearcher.isMainMethod(method)

  private fun getMainMethodCount(clazz: PsiClass): Int {
    return CachedValuesManager.getCachedValue(
      clazz,
      CachedValueProvider {
        val methodCount = GroovyMainMethodSearcher
          .findMainMethodsInClassByName(clazz)
          .count { GroovyMainMethodSearcher.isMainMethod(it) }
        CachedValueProvider.Result(methodCount,
                                   PsiModificationTracker.MODIFICATION_COUNT)
      }
    )
  }
}

