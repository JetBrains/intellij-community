// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import org.jetbrains.kotlin.idea.base.highlighting.AbstractKotlinHighlightExitPointsHandlerFactory
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunction
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class K1HighlightExitPointsHandlerFactory : AbstractKotlinHighlightExitPointsHandlerFactory() {

    override fun getRelevantReturnDeclaration(returnExpression: KtReturnExpression): KtDeclarationWithBody? {
        val targetFunction = returnExpression.getTargetFunction(returnExpression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)) as? KtDeclarationWithBody
        return targetFunction
    }

    override fun isInlinedArgument(declaration: KtDeclarationWithBody): Boolean =
      InlineUtil.isInlinedArgument(
        declaration as KtFunction,
        declaration.safeAnalyzeNonSourceRootCode(BodyResolveMode.FULL),
        false
      )
}