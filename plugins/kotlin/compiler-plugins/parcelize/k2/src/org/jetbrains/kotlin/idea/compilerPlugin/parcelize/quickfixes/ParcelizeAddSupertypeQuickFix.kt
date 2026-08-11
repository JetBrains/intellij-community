// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.parcelize.ParcelizeNames
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPsiFactory

class ParcelizeAddSupertypeQuickFix(clazz: KtClassOrObject) : AbstractParcelizePsiOnlyQuickFix<KtClassOrObject>(clazz) {
    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.add.parcelable.supertype")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtClassOrObject) {
        val supertypeName = ParcelizeNames.PARCELABLE_ID.asFqNameString()
        shortenReferences(element.addSuperTypeListEntry(ktPsiFactory.createSuperTypeEntry(supertypeName)))
    }

    companion object {
        val FACTORY = factory(::ParcelizeAddSupertypeQuickFix)
    }
}
