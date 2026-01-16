/*
 * Copyright (C) 2023 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.k2.intentions

import com.android.tools.compose.expectedComposableAnnotationHolder
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaCompilerPluginDiagnostic0
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * Registers QuickFixes for Compose compiler diagnostics in K2 mode
 *
 * Based on: [com.android.tools.compose.intentions.AddComposableAnnotationQuickFix]
 */
internal class K2CreateComposableQuickFixesRegistrar : KotlinQuickFixRegistrar() {

  /**
   * Creates a fix for the COMPOSABLE_INVOCATION error, which appears on a Composable function call
   * from within a non-Composable scope.
   */
  private val composableInvocationFactory = KotlinQuickFixFactory.IntentionBased create@{ diagnostic: KaCompilerPluginDiagnostic0 ->
    if (diagnostic.factoryName != "COMPOSABLE_INVOCATION") return@create emptyList()

    val psiElement = diagnostic.psi
    val node = (psiElement as? KtElement)?.expectedComposableAnnotationHolder()
    if (node == null || !node.isValid || !node.isWritable) return@create emptyList()

    val text = node.toDisplayText()
    if (text == null) return@create emptyList()

    listOf(K2AddComposableAnnotationQuickFix(node, text))
  }

  /**
   * Creates a fix for the COMPOSABLE_EXPECTED error, which appears on a non-Composable scope that
   * contains a Composable function call.
   */
  private val composableExpectedFactory = KotlinQuickFixFactory.IntentionBased create@{ diagnostic: KaCompilerPluginDiagnostic0 ->
    if (diagnostic.factoryName != "COMPOSABLE_EXPECTED") return@create emptyList()

    val psiElement = diagnostic.psi
    val node = psiElement.parentsOfType<KtModifierListOwner>(withSelf = true)
      .firstOrNull { it is KtNamedFunction || it is KtProperty }

    if (node == null || !node.isValid || !node.isWritable) return@create emptyList()

    val target = when (node) {
      is KtProperty -> {
        // If there is only one accessor, and it is a getter, use that
        node.accessors.singleOrNull()?.takeIf { it.isGetter }
      }
      else -> node
   } ?: return@create emptyList()

    val text = target.toDisplayText()
    if (text == null) return@create emptyList()

    listOf(K2AddComposableAnnotationQuickFix(target, text))
  }

  private fun KtModifierListOwner.toDisplayText(): String? =
    when (this) {
      is KtTypeReference -> toDisplayText()
      is KtNamedFunction ->
        name?.let { ComposeIdeBundle.message("compose.add.composable.to.element.name", it) }
        ?: ComposeIdeBundle.message("compose.add.composable.to.anonymous.function.name")
      is KtFunctionLiteral -> ComposeIdeBundle.message("compose.add.composable.to.enclosing.lambda.name")
      is KtPropertyAccessor ->
        takeIf(KtPropertyAccessor::isGetter)?.property?.name?.let {
          ComposeIdeBundle.message("compose.add.composable.to.element.name", "$it.get()")
        }
      else -> name?.let { ComposeIdeBundle.message("compose.add.composable.to.element.name", it) }
    }

  private fun KtTypeReference.toDisplayText(): String? {
    val param = parent as? KtParameter

    if (param == null) {
      val propertyName = (parent as? KtProperty)?.name ?: return null
      return ComposeIdeBundle.message("compose.add.composable.to.property.type.name", propertyName)
    }

    val paramName = param.name ?: return null
    val functionName = (param.parent?.parent as? KtNamedFunction)?.name

    return functionName?.let {
      ComposeIdeBundle.message("compose.add.composable.to.lambda.parameter.name", functionName, paramName, )
    } ?: ComposeIdeBundle.message("compose.add.composable.to.lambda.parameter.of.anonymous.function.name", paramName)
  }

  override val list: KotlinQuickFixesList = KtQuickFixesListBuilder.registerPsiQuickFix {
    registerFactory(composableInvocationFactory)
    registerFactory(composableExpectedFactory)
  }
}
