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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.BaseScopeProcessor
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint.KEY
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns

/**
 * @author Vladislav.Soroka
 * @since 11/11/2016
 */

fun processDeclarations(handlerClass: PsiClass,
                        processor: PsiScopeProcessor,
                        state: ResolveState,
                        place: PsiElement): Boolean {
  val name = processor.getHint(KEY)?.getName(state)
  val componentClass = handlerClass
  val componentProcessor = ComponentProcessor(processor, place, name)
  if (name == null) {
    if (!componentClass.processDeclarations(componentProcessor, state, null, place)) return false
  }
  else {
    for (method in componentClass.findMethodsByName(name, true)) {
      if (!componentProcessor.execute(method, state)) return false
    }
    for (prefix in arrayOf("add", "set")) {
      for (method in componentClass.findMethodsByName(prefix + name.capitalize(), true)) {
        if (!componentProcessor.execute(method, state)) return false
      }
    }
  }
  return true
}

class ComponentProcessor(val delegate: PsiScopeProcessor, val place: PsiElement, val name: String?) : BaseScopeProcessor() {

  override fun execute(method: PsiElement, state: ResolveState): Boolean {
    method as? PsiMethod ?: return true
    return delegate.execute(method, state)
  }

  override fun <T : Any?> getHint(hintKey: Key<T>): T? = if (hintKey == com.intellij.psi.scope.ElementClassHint.KEY) {
    @Suppress("UNCHECKED_CAST")
    ElementClassHint { it == com.intellij.psi.scope.ElementClassHint.DeclarationKind.METHOD } as T
  }
  else {
    null
  }
}

fun psiMethodInClass(containingClass: String) = GroovyPatterns.psiMethod().definedInClass(containingClass)