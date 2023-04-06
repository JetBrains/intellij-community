// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPsiFactory

class AnnotateWithParcelizeQuickFix(clazz: KtClassOrObject) : AbstractParcelizePsiOnlyQuickFix<KtClassOrObject>(clazz) {
    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.annotate.containing.class.with.parcelize")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtClassOrObject) {
        element.addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@kotlinx.parcelize.Parcelize")).shortenReferences()
    }

    // No factory - this quickfix needs targeting information from the diagnostic.
}
