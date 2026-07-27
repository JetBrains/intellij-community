// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

class ParcelizeAddIgnoreOnParcelAnnotationQuickFix(property: KtProperty) : AbstractParcelizePsiOnlyQuickFix<KtProperty>(property) {
    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.add.ignored.on.parcel.annotation")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtProperty) {
        shortenReferences(element.addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@kotlinx.parcelize.IgnoredOnParcel")))
    }

    companion object {
        val FACTORY = factory(::ParcelizeAddIgnoreOnParcelAnnotationQuickFix)
    }
}