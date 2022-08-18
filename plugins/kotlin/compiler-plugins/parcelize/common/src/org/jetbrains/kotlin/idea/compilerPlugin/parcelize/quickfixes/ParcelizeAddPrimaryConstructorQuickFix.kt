// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createPrimaryConstructorIfAbsent

class ParcelizeAddPrimaryConstructorQuickFix(clazz: KtClass) : AbstractParcelizeQuickFix<KtClass>(clazz) {
    object Factory : AbstractFactory({ findElement<KtClass>()?.let(::ParcelizeAddPrimaryConstructorQuickFix) })

    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.add.empty.primary.constructor")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtClass) {
        element.createPrimaryConstructorIfAbsent()

        for (secondaryConstructor in element.secondaryConstructors) {
            if (secondaryConstructor.getDelegationCall().isImplicit) {
                val parameterList = secondaryConstructor.valueParameterList ?: return
                val colon = secondaryConstructor.addAfter(ktPsiFactory.createColon(), parameterList)
                secondaryConstructor.addAfter(ktPsiFactory.createExpression("this()"), colon)
            }
        }
    }
}