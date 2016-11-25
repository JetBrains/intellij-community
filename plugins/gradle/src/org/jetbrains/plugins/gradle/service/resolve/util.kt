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
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.gradle.util.GradleConstants.EXTENSION
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns
import java.util.*

/**
 * @author Vladislav.Soroka
 * @since 11/11/2016
 */
internal fun PsiClass?.isResolvedInGradleScript() = this is GroovyScriptClass && this.containingFile.isGradleScript()

internal fun PsiFile?.isGradleScript() = this?.originalFile?.virtualFile?.extension == EXTENSION

@JvmField val RESOLVED_CODE = Key.create<Boolean?>("gradle.resolved")

fun processDeclarations(aClass: PsiClass,
                        processor: PsiScopeProcessor,
                        state: ResolveState,
                        place: PsiElement): Boolean {
  val name = processor.getHint(com.intellij.psi.scope.NameHint.KEY)?.getName(state)
  if (name == null) {
    aClass.processDeclarations(processor, state, null, place)
  }
  else {
    val isSetterCandidate = name.startsWith("set")
    val processedSignatures = HashSet<List<String>>()
    for (method in aClass.findMethodsByName(name, true)) {
      if (!isSetterCandidate) {
        processedSignatures.add(method.getSignature(PsiSubstitutor.EMPTY).parameterTypes.map({ it.canonicalText }))
      }
      place.putUserData(RESOLVED_CODE, true)
      if (!processor.execute(method, state)) return false
    }

    if (!isSetterCandidate) {
      for (method in aClass.findMethodsByName("set" + name.capitalize(), true)) {
        if (processedSignatures.contains(method.getSignature(PsiSubstitutor.EMPTY).parameterTypes.map({ it.canonicalText }))) continue
        place.putUserData(RESOLVED_CODE, true)
        if (!processor.execute(method, state)) return false
      }
    }
  }
  return true
}

fun psiMethodInClass(containingClass: String) = GroovyPatterns.psiMethod().definedInClass(containingClass)