// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

class ParcelizeAddIgnoreOnParcelAnnotationQuickFix(property: KtProperty) : AbstractParcelizePsiOnlyQuickFix<KtProperty>(property) {
    object Factory : AbstractQuickFixFactory({ findElement<KtProperty>()?.let(::ParcelizeAddIgnoreOnParcelAnnotationQuickFix) })

    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.add.ignored.on.parcel.annotation")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtProperty) {
        element.addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@kotlinx.parcelize.IgnoredOnParcel")).shortenReferences()
    }
}