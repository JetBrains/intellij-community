// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils
import org.jetbrains.uast.UClass

internal class ListenerImplementationMustNotBeDisposableInspection : DevKitUastInspectionBase(UClass::class.java) {

  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val psiClass = aClass.javaPsi

    if (psiClass.classKind != JvmClassKind.CLASS ||
        PsiUtil.isAbstractClass(psiClass) ||
        PsiUtil.isLocalOrAnonymousClass(psiClass) ||
        PsiUtil.isInnerClass(psiClass) ||
        psiClass.qualifiedName == null) return ProblemDescriptor.EMPTY_ARRAY

    if (!InheritanceUtil.isInheritor(psiClass, Disposable::class.java.canonicalName)) return ProblemDescriptor.EMPTY_ARRAY

    if (!isRegisteredListener(psiClass)) return ProblemDescriptor.EMPTY_ARRAY

    val holder = createProblemsHolder(aClass, manager, isOnTheFly)
    holder.registerUProblem(
      aClass,
      DevKitBundle.message("inspections.listener.implementation.must.not.implement.disposable")
    )
    return holder.resultsArray
  }

  private fun isRegisteredListener(psiClass: PsiClass): Boolean {
    val project = psiClass.project
    return !IdeaPluginRegistrationIndex.processListener(
      project,
      psiClass,
      PluginRelatedLocatorsUtils.getCandidatesScope(project),
    ) { false }
  }

}
