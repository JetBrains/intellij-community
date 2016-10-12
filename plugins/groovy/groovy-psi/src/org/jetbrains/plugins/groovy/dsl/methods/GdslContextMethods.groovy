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
package org.jetbrains.plugins.groovy.dsl.methods

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.dsl.toplevel.Context

@CompileStatic
@SuppressWarnings(["GrMethodMayBeStatic", "GroovyUnusedDeclaration"])
trait GdslContextMethods {
  /**
   * Context definition
   */
  def context(Map args) { return new Context(args) }

  def hasAnnotation(String annoQName) { PsiJavaPatterns.psiModifierListOwner().withAnnotation(annoQName) }

  def hasField(ElementPattern fieldCondition) { PsiJavaPatterns.psiClass().withField(true, PsiJavaPatterns.psiField().and(fieldCondition)) }

  def hasMethod(ElementPattern methodCondition) {
    PsiJavaPatterns.psiClass().withMethod(true, PsiJavaPatterns.psiMethod().and(methodCondition))
  }
}
