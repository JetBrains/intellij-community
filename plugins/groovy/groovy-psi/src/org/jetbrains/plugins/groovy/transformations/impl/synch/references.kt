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
package org.jetbrains.plugins.groovy.transformations.impl.synch

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

class SynchronizedReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PATTERN, SynchronizedReferenceProvider())
  }
}

class SynchronizedReferenceProvider : PsiReferenceProvider() {

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference> {
    val literal = element as? GrLiteral ?: return emptyArray()
    return arrayOf(FieldReference(literal))
  }
}

class FieldReference(literal: GrLiteral) : PsiReferenceBase<GrLiteral>(literal) {

  private val GrLiteral.containingClass: GrTypeDefinition?
    get() = PsiTreeUtil.getParentOfType(this, GrTypeDefinition::class.java)

  override fun getVariants(): Array<out Any> = element.containingClass?.codeFields ?: emptyArray()

  override fun resolve(): PsiField? = element.containingClass?.findCodeFieldByName(value, false)
}