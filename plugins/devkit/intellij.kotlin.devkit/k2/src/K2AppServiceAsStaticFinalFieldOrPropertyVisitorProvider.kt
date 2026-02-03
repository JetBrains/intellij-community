// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.k2

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.idea.devkit.kotlin.inspections.KtAppServiceAsStaticFinalFieldOrPropertyVisitorProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ConvertPropertyToFunctionIntention

internal class K2AppServiceAsStaticFinalFieldOrPropertyVisitorProvider : KtAppServiceAsStaticFinalFieldOrPropertyVisitorProvider() {
  override fun getConvertPropertyToFunctionIntention(): IntentionAction =
    ConvertPropertyToFunctionIntention().asIntention()
}
