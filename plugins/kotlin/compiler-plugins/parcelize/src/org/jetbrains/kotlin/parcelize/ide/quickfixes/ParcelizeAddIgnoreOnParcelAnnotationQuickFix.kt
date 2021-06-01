// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.parcelize.ide.quickfixes

import kotlinx.parcelize.IgnoredOnParcel
import org.jetbrains.kotlin.parcelize.ide.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

class ParcelizeAddIgnoreOnParcelAnnotationQuickFix(property: KtProperty) : AbstractParcelizeQuickFix<KtProperty>(property) {
    object Factory : AbstractFactory({ findElement<KtProperty>()?.let(::ParcelizeAddIgnoreOnParcelAnnotationQuickFix) })

    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.add.ignored.on.parcel.annotation")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtProperty) {
        element.addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@" + IgnoredOnParcel::class.java.name)).shortenReferences()
    }
}