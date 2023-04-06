// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFix
import org.jetbrains.kotlin.idea.util.createIntentionFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.parcelize.diagnostic.ErrorsParcelize

class K1ParcelizeQuickFixContributor : QuickFixContributor {
    override fun registerQuickFixes(quickFixes: QuickFixes) {
        quickFixes.register(
            ErrorsParcelize.PARCELABLE_CANT_BE_INNER_CLASS,
            RemoveModifierFix.createRemoveModifierFromListOwnerFactory(KtTokens.INNER_KEYWORD, false)
        )

        quickFixes.register(ErrorsParcelize.NO_PARCELABLE_SUPERTYPE, ParcelizeAddSupertypeQuickFix.FACTORY)
        quickFixes.register(ErrorsParcelize.PARCELABLE_SHOULD_HAVE_PRIMARY_CONSTRUCTOR, ParcelizeAddPrimaryConstructorQuickFix.FACTORY)
        quickFixes.register(ErrorsParcelize.PROPERTY_WONT_BE_SERIALIZED, ParcelizeAddIgnoreOnParcelAnnotationQuickFix.FACTORY)

        quickFixes.register(ErrorsParcelize.OVERRIDING_WRITE_TO_PARCEL_IS_NOT_ALLOWED, ParcelMigrateToParcelizeQuickFix.FACTORY_FOR_WRITE)
        quickFixes.register(ErrorsParcelize.OVERRIDING_WRITE_TO_PARCEL_IS_NOT_ALLOWED, ParcelRemoveCustomWriteToParcel.FACTORY)

        quickFixes.register(ErrorsParcelize.CREATOR_DEFINITION_IS_NOT_ALLOWED, ParcelMigrateToParcelizeQuickFix.FACTORY_FOR_CREATOR)
        quickFixes.register(ErrorsParcelize.CREATOR_DEFINITION_IS_NOT_ALLOWED, ParcelRemoveCustomCreatorProperty.FACTORY)

        quickFixes.register(ErrorsParcelize.REDUNDANT_TYPE_PARCELER, ParcelizeRemoveDuplicatingTypeParcelerAnnotationQuickFix.FACTORY)

        quickFixes.register(
            ErrorsParcelize.CLASS_SHOULD_BE_PARCELIZE,
            createIntentionFactory { AnnotateWithParcelizeQuickFix(ErrorsParcelize.CLASS_SHOULD_BE_PARCELIZE.cast(it).a) }
        )
    }
}