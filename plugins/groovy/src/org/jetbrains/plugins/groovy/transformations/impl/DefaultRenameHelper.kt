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
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMember
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.getAccessorName
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.getSetterName
import org.jetbrains.plugins.groovy.refactoring.rename.GrRenameHelper

class DefaultRenameHelper : GrRenameHelper {

  override fun getNewMemberName(member: PsiMember, newOriginalName: String): String? {
    if (member !is GrAccessorMethod) return null
    return if (member.isSetter) {
      getSetterName(newOriginalName)
    }
    else {
      getAccessorName(if (member.name.startsWith("is")) "is" else "get", newOriginalName)
    }
  }

  override fun isQualificationNeeded(manager: PsiManager,
                                     before: PsiElement,
                                     after: PsiElement): Boolean {
    return before is GrAccessorMethod && (after !is GrAccessorMethod || !manager.areElementsEquivalent(before.property, after.property))
  }
}