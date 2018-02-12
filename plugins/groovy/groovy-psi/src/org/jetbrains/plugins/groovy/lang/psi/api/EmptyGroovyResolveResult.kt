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
package org.jetbrains.plugins.groovy.lang.psi.api

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor

object EmptyGroovyResolveResult : GroovyResolveResult {

  override fun getElement(): PsiElement? = null

  override fun isApplicable(): Boolean = false

  override fun isAccessible(): Boolean = false

  override fun getCurrentFileResolveContext(): PsiElement? = null

  override fun isStaticsOK(): Boolean = true

  override fun getSubstitutor(): PsiSubstitutor = PsiSubstitutor.EMPTY

  override fun isValidResult(): Boolean = false

  override fun isInvokedOnProperty(): Boolean = false

  override fun getSpreadState(): SpreadState? = null
}
