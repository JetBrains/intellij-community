// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.java

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReferenceBase
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.jetbrains.uast.ULiteralExpression

/**
 * Provides completion for available Language-IDs in
 * <pre>@Language("[ctrl-space]")</pre>
 */
class ULiteralLanguageReference(val uLiteralExpression: ULiteralExpression,
                                val host: PsiLanguageInjectionHost) : PsiReferenceBase<PsiLanguageInjectionHost>(host) {

  override fun getValue(): String = uLiteralExpression.value as? String ?: ""

  override fun resolve(): PsiElement? = if (InjectedLanguage.findLanguageById(value) != null) uLiteralExpression.sourcePsi else null

  override fun isSoft(): Boolean = false

  override fun getVariants(): Array<LookupElement> =
    InjectLanguageAction.getAllInjectables().map {
      LookupElementBuilder.create(it.id).withIcon(it.icon).withTailText("(${it.displayName})", true)
    }.toTypedArray()
}
