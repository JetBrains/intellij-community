// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.k1

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.idea.devkit.kotlin.inspections.KtAppServiceAsStaticFinalFieldOrPropertyVisitorProvider
import org.jetbrains.kotlin.idea.intentions.ConvertPropertyToFunctionIntention

internal class K1AppServiceAsStaticFinalFieldOrPropertyVisitorProvider : KtAppServiceAsStaticFinalFieldOrPropertyVisitorProvider() {
  override fun getConvertPropertyToFunctionIntention(): IntentionAction =
    ConvertPropertyToFunctionIntention()
}
