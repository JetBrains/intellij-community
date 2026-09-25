// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.highlighting.BeforeResolveHighlightingExtension
import org.jetbrains.kotlin.idea.base.highlighting.NamedArgumentsHighlightingRefinement
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

// Paints `name =` of named arguments without resolve, for products that bundle only this minimal module.
internal class NamedArgumentsHighlightingVisitor(
    holder: HighlightInfoHolder
) : AbstractHighlightingVisitor(holder), DumbAware {
    override fun visitArgument(argument: KtValueArgument) {
        val argumentName = argument.getArgumentName() ?: return
        val eq = argument.equalsToken ?: return
        val parent = argument.parent

        val infoType = if (parent is KtValueArgumentList && parent.parent is KtAnnotationEntry) {
            KotlinHighlightInfoTypeSemanticNames.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES
        } else {
            KotlinHighlightInfoTypeSemanticNames.NAMED_ARGUMENT
        }

        highlightName(argument.project, argument, TextRange(argumentName.startOffset, eq.endOffset), infoType)
    }
}

private class NoOpHighlightingVisitor(holder: HighlightInfoHolder) : AbstractHighlightingVisitor(holder)

class NamedArgumentsHighlightingExtension : BeforeResolveHighlightingExtension {
    override fun createVisitor(holder: HighlightInfoHolder): AbstractHighlightingVisitor {
        // A resolve-aware extension (K2) owns named arguments when present; avoid a second, conflicting info per range.
        val refined = BeforeResolveHighlightingExtension.EP_NAME.extensionList.any { it is NamedArgumentsHighlightingRefinement }
        return if (refined) NoOpHighlightingVisitor(holder) else NamedArgumentsHighlightingVisitor(holder)
    }
}
