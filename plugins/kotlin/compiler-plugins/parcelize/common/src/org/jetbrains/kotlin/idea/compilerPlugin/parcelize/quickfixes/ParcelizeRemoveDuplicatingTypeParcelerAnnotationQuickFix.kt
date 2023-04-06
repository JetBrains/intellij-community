// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory

class ParcelizeRemoveDuplicatingTypeParcelerAnnotationQuickFix(anno: KtAnnotationEntry) : AbstractParcelizePsiOnlyQuickFix<KtAnnotationEntry>(anno) {
    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.remove.redundant.type.parceler.annotation")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtAnnotationEntry) {
        element.delete()
    }

    companion object {
        val FACTORY = factory(::ParcelizeRemoveDuplicatingTypeParcelerAnnotationQuickFix)
    }
}