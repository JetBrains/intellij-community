/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.parcelize.ParcelizeNames
import org.jetbrains.kotlin.psi.*

class ParcelizeAddSupertypeQuickFix(clazz: KtClassOrObject) : AbstractParcelizePsiOnlyQuickFix<KtClassOrObject>(clazz) {
    object Factory : AbstractQuickFixFactory({ findElement<KtClassOrObject>()?.let(::ParcelizeAddSupertypeQuickFix) })

    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.add.parcelable.supertype")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtClassOrObject) {
        val supertypeName = ParcelizeNames.PARCELABLE_ID.asFqNameString()
        element.addSuperTypeListEntry(ktPsiFactory.createSuperTypeEntry(supertypeName)).shortenReferences()
    }
}
