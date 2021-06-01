// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.parcelize.ide.quickfixes

import org.jetbrains.kotlin.parcelize.ANDROID_PARCELABLE_CLASS_FQNAME
import org.jetbrains.kotlin.parcelize.ide.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.*

class ParcelizeAddSupertypeQuickFix(clazz: KtClassOrObject) : AbstractParcelizeQuickFix<KtClassOrObject>(clazz) {
    object Factory : AbstractFactory({ findElement<KtClassOrObject>()?.let(::ParcelizeAddSupertypeQuickFix) })

    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.add.parcelable.supertype")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtClassOrObject) {
        val supertypeName = ANDROID_PARCELABLE_CLASS_FQNAME.asString()
        element.addSuperTypeListEntry(ktPsiFactory.createSuperTypeEntry(supertypeName)).shortenReferences()
    }
}