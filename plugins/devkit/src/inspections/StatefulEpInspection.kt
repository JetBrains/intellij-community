/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.idea.devkit.util.ExtensionPointLocator
import java.util.*
import kotlin.reflect.KClass

class StatefulEpInspection : DevKitInspectionBase() {
  override fun checkClass(psiClass: PsiClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (psiClass.fields.isEmpty()) return super.checkClass(psiClass, manager, isOnTheFly)
    val isQuickFix = InheritanceUtil.isInheritor(psiClass, com.intellij.codeInspection.LocalQuickFix::class.java.canonicalName)
    if (!isQuickFix && !shouldCheck(psiClass)) return super.checkClass(psiClass, manager, isOnTheFly)
    val result = arrayListOf<ProblemDescriptor>()
    for (field in psiClass.fields) {
      val projectChecker = Checker(Project::class,
          {
            val modifierList = field.modifierList ?: return@Checker true
            !modifierList.hasModifierProperty(PsiModifier.FINAL)
          },
          "")
      val psiChecker = Checker(PsiElement::class,
          { true },
          "Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead of" +
              if (isQuickFix) "; also see LocalQuickFixOnPsiElement" else "")

      for (t in listOf(projectChecker, psiChecker)) checkFor(field, t, manager, isOnTheFly, result)
    }
    return result.toArray(result.toArray<ProblemDescriptor>(arrayOfNulls<ProblemDescriptor>(result.size)))
  }

  private fun checkFor(field: PsiField, checker: Checker<KClass<out Any>, () -> Boolean, String>, manager: InspectionManager,
                       isOnTheFly: Boolean, result: ArrayList<ProblemDescriptor>) {
    if (checker.predicate() && InheritanceUtil.isInheritor(field.type, checker.clazz.java.canonicalName)) {
      val descriptor = manager.createProblemDescriptor(field,
          if (checker.message.isNotEmpty()) checker.message else "Don't use ${checker.clazz.simpleName} as a field in extension points",
          true, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)
      result.add(descriptor)
    }
  }

  fun shouldCheck(psiClass: PsiClass): Boolean {
    for (c in ExtensionPointLocator(psiClass).findSuperCandidates()) {
      if (ExtensionPointLocator.isImplementedEp(psiClass, c)) return true
    }
    return false
  }

  data class Checker<out A, B, C>(val clazz: A, val predicate: B, val message: C) 
}