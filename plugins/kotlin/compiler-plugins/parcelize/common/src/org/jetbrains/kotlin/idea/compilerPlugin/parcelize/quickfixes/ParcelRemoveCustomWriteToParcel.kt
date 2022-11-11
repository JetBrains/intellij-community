// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

class ParcelRemoveCustomWriteToParcel(function: KtFunction) : AbstractParcelizeQuickFix<KtFunction>(function) {
    object Factory : AbstractFactory({ findElement<KtFunction>()?.let(::ParcelRemoveCustomWriteToParcel) })

    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.remove.custom.write.to.parcel.function")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtFunction) {
        element.delete()
    }
}