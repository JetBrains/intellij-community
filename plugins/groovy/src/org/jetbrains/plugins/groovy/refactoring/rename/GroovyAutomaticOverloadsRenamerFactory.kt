/*
 * Copyright 2000-2016 JetBrains s.r.o.
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


package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.rename.naming.AutomaticOverloadsRenamer
import com.intellij.refactoring.rename.naming.AutomaticOverloadsRenamerFactory
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod

class GroovyAutomaticOverloadsRenamerFactory : AutomaticOverloadsRenamerFactory() {

  override fun isApplicable(element: PsiElement): Boolean {
    if (element !is GrMethod || element.isConstructor) return false
    val containingClass = element.containingClass ?: return false
    val allSameNameMethods = containingClass.findMethodsByName(element.name, false)
    return allSameNameMethods.filter {
      it !is GrReflectedMethod || it.baseMethod !== element
    }.isNotEmpty()
  }

  override fun createRenamer(element: PsiElement?, newName: String?, usages: MutableCollection<UsageInfo>?): AutomaticRenamer {
    return object : AutomaticOverloadsRenamer(element as GrMethod, newName) {
      override fun getOverloads(method: PsiMethod): Array<out PsiMethod> {
        val containingClass = method.containingClass ?: return PsiMethod.EMPTY_ARRAY
        val allSameNameMethods = containingClass.findMethodsByName(method.name, false)
        return allSameNameMethods.mapNotNullTo(mutableSetOf()) {
          if (it !is GrReflectedMethod) {
            it
          }
          else {
            val baseMethod = it.baseMethod
            if (baseMethod !== method) baseMethod else null
          }
        }.toTypedArray()
      }
    }
  }
}