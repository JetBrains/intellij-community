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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.psi.*

class FromAbstractTypeMethodsHintProcessor : SignatureHintProcessor() {

  override fun getHintName(): String = "groovy.transform.stc.FromAbstractTypeMethods"

  override fun inferExpectedSignatures(method: PsiMethod, substitutor: PsiSubstitutor, options: Array<String>): List<Array<PsiType>> {
    val qname = options.singleOrNull() ?: return emptyList()
    val aClass = JavaPsiFacade.getInstance(method.project).findClass(qname, method.resolveScope) ?: return emptyList()
    return aClass.visibleSignatures.filter {
      it.method.hasModifierProperty(PsiModifier.ABSTRACT)
    }.map {
      it.parameterTypes
    }
  }
}
