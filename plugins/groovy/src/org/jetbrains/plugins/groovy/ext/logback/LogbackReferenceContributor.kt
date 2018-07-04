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
package org.jetbrains.plugins.groovy.ext.logback

import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.PsiReferenceRegistrar.HIGHER_PRIORITY
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyElementPattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyList
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod

class LogbackReferenceContributor : PsiReferenceContributor() {

  companion object {
    val appenderReferencePlace: GroovyElementPattern.Capture<GrLiteral> = GroovyPatterns.groovyLiteralExpression().withParent(
        StandardPatterns.or(
            groovyList().methodCallParameter(1, psiMethod(configDelegateFqn, "root")),
            groovyList().methodCallParameter(2, psiMethod(configDelegateFqn, "logger"))
        )
    )
  }

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    val provider = object : PsiReferenceProvider() {
      override fun getReferencesByElement(element: PsiElement, context: ProcessingContext) = arrayOf(
          AppenderReference(element as GrLiteral)
      )
    }
    registrar.registerReferenceProvider(appenderReferencePlace, provider, HIGHER_PRIORITY)
  }
}