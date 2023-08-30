// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class ListenerImplementationMustNotBeDisposableInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {

        override fun visitClass(node: UClass): Boolean {
          val psiClass = node.javaPsi

          if (psiClass.classKind != JvmClassKind.CLASS ||
              PsiUtil.isAbstractClass(psiClass) ||
              PsiUtil.isLocalOrAnonymousClass(psiClass) ||
              PsiUtil.isInnerClass(psiClass) ||
              psiClass.qualifiedName == null) return true

          if (!InheritanceUtil.isInheritor(psiClass, Disposable::class.java.canonicalName)) return true

          val isRegisteredListener = !IdeaPluginRegistrationIndex.processListener(
            holder.project,
            psiClass,
            PluginRelatedLocatorsUtils.getCandidatesScope(holder.project),
          ) { false }

          if (!isRegisteredListener) return true

          holder.registerUProblem(
            node,
            DevKitBundle.message("inspections.listener.implementation.must.not.implement.disposable")
          )
          return true
        }
      },
      arrayOf(UClass::class.java)
    )
  }
}
