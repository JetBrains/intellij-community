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

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.PomTargetPsiElementImpl
import icons.JetgroovyIcons.Groovy.Groovy_16x16
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

internal class AppenderReference(literal: GrLiteral) : PsiPolyVariantReferenceBase<GrLiteral>(literal) {

  override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> {
    if (incompleteCode) return emptyArray()
    val declaration = element.containingFile.appenderDeclarations[value] ?: return emptyArray()
    if (!declaration.isBefore(element)) return emptyArray()
    val target = AppenderTarget(declaration)
    val targetPsi = object : PomTargetPsiElementImpl(target) {
      override fun getNavigationElement() = this // hack
      // Reference under caret is resolved to this element.
      //
      // GotoDeclarationAction asks this element to provide the navigation element.
      // PomTargetPsiElementImpl asks its target to provide the navigation element.
      // Target is an AppenderTarget bound to GrLiteral.
      // GotoDeclarationAction gets GrLiteral and calls navigate() and the cursor is placed to the start offset of GrLiteral,
      // i.e. <caret>"appender_name".
      //
      // To override this behaviour we return `this` so when GotoDeclarationAction calls navigate()
      // the PomTargetPsiElementImpl will delegate it to its target properly placing the cursor inside the literal,
      // i.e. "<caret>appender_name"
    }
    val resolveResult = PsiElementResolveResult(targetPsi)
    return arrayOf(resolveResult)
  }

  override fun getVariants() = element.containingFile.appenderDeclarations.filterValues {
    it.isBefore(element)
  }.keys.map {
    LookupElementBuilder.create(it).withTypeText("Appender", true).withIcon(Groovy_16x16)
  }.toTypedArray()
}
