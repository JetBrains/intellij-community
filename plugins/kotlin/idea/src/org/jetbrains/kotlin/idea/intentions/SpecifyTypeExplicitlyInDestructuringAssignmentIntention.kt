// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.isError

class SpecifyTypeExplicitlyInDestructuringAssignmentIntention : SelfTargetingRangeIntention<KtDestructuringDeclaration>(
    KtDestructuringDeclaration::class.java, KotlinBundle.lazyMessage("specify.all.types.explicitly.in.destructuring.declaration")
) {
    override fun applicabilityRange(element: KtDestructuringDeclaration): TextRange? {
        if (element.containingFile is KtCodeFragment) return null
        val entries = element.entriesWithoutExplicitTypes()
        if (entries.isEmpty()) return null
        if (entries.any { SpecifyTypeExplicitlyIntention.getTypeForDeclaration(it).isError }) return null
        val endOffset = element.initializer?.let { it.startOffset - 1 } ?: element.endOffset
        return TextRange(element.startOffset, endOffset)
    }

    override fun applyTo(element: KtDestructuringDeclaration, editor: Editor?) {
        val entries = element.entriesWithoutExplicitTypes()
        if (editor != null && element.getParentOfType<KtParameterList>(strict = false) == null) {
            SpecifyTypeExplicitlyIntention.addTypeAnnotationWithTemplate(editor, entries.iterator())
        } else {
            for (entry in entries) {
                entry.setType(SpecifyTypeExplicitlyIntention.getTypeForDeclaration(entry))
            }
        }
    }
}

private fun KtDestructuringDeclaration.entriesWithoutExplicitTypes(): List<KtDestructuringDeclarationEntry> =
    entries.filter { it.typeReference == null }
