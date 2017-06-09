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

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.intellij.plugins.intelliLang.Configuration
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.jetbrains.uast.*
import java.util.*

class UastLanguageInjector : MultiHostInjector {

  private val uastSupport: UastLanguageInjectionSupport? by lazy {
    ArrayList(InjectorUtils.getActiveInjectionSupports()).filterIsInstance(UastLanguageInjectionSupport::class.java).firstOrNull()
  }

  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {

    val uLiteralExpression = findULiteralUnder(context)
    val injectionInfo = uLiteralExpression?.let { detectInjection(it) }

    val uastSupport = uastSupport
    if (injectionInfo != null && uastSupport != null) {
      injectionInfo.toBaseInjection(uastSupport)?.let {
        InjectorUtils.registerInjectionSimple(context as PsiLanguageInjectionHost, it, uastSupport, registrar)
      }
    }

  }

  private fun findULiteralUnder(context: PsiElement): ULiteralExpression? {
    return context.toUElements().filterIsInstance<ULiteralExpression>().firstOrNull()
  }

  private fun detectInjection(context: ULiteralExpression): InjectionInfo? {
    return detectAnnotationMethodParam(context)
           ?: detectMethodParam(context)
  }

  private fun detectMethodParam(context: ULiteralExpression): InjectionInfo? {
    val injections = Configuration.getInstance().getInjections(UAST_SUPPORT_ID)
    val callExpression = context.uastParent as? UCallExpression ?: return null
    val i = callExpression.valueArguments.indexOfFirst { it.psi === context.psi }
    val parameter = callExpression.resolve()?.parameterList?.parameters?.elementAtOrNull(i) ?: return null
    return parameter.let { findInjection(it, injections, context) }
  }

  private fun detectAnnotationMethodParam(context: ULiteralExpression): InjectionInfo? {
    val injections = Configuration.getInstance().getInjections(UAST_SUPPORT_ID)
    val nameValuePair = context.uastParent as? UNamedExpression ?: return null
    val uAnnotation = nameValuePair.uastParent as? UAnnotation ?: return null
    val reference = uAnnotation.resolve() ?: return null
    val name = nameValuePair.name.takeIf { !it.isNullOrBlank() } ?: "value"
    val method = reference.findMethodsByName(name, false).singleOrNull() ?: return null
    return method.let { findInjection(it, injections, context) }
  }

  override fun elementsToInjectIn() = listOf(PsiLanguageInjectionHost::class.java)

  private fun findInjection(element: PsiElement?,
                            injections: List<BaseInjection>,
                            uLiteral: ULiteralExpression): InjectionInfo? {
    for (injection in injections) {
      if (injection.acceptsPsiElement(element)) {
        return InjectionInfo(injection.injectedLanguageId, injection.prefix, injection.suffix, uLiteral)
      }
    }

    return null
  }

  //copied from KotlinLanguageInjector
  private class InjectionInfo(val languageId: String?, val prefix: String?, val suffix: String?, val uElement: UElement) {
    fun toBaseInjection(injectionSupport: LanguageInjectionSupport): BaseInjection? {
      if (languageId == null) return null

      val baseInjection = BaseInjection(injectionSupport.id)
      baseInjection.injectedLanguageId = languageId

      if (prefix != null) {
        baseInjection.prefix = prefix
      }

      if (suffix != null) {
        baseInjection.suffix = suffix
      }

      return baseInjection
    }
  }

}
