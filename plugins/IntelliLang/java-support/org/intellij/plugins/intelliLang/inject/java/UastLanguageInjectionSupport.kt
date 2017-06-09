/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject.java

import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.containers.SmartHashSet
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.UastLanguagePlugin

@NonNls val UAST_SUPPORT_ID = "uast"

class UastLanguageInjectionSupport : AbstractLanguageInjectionSupport() {
  override fun getId(): String = UAST_SUPPORT_ID

  override fun getSupportedIds(): Set<String> = UastLanguagePlugin.getInstances().mapTo(SmartHashSet()) { it.language.id }

  override fun getPatternClasses(): Array<Class<*>> = arrayOf(PsiJavaPatterns::class.java)

  override fun isApplicableTo(host: PsiLanguageInjectionHost): Boolean =
    UastLanguagePlugin.getInstances().any { it.language == host.language }

  override fun useDefaultInjector(host: PsiLanguageInjectionHost): Boolean = true

}