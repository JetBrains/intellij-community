// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.createFromUsage

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

object CreateParameterUtil {
    private fun chooseContainingClass(psiElement: PsiElement, varExpected: Boolean): Pair<KtClass?,ValVar> {
        return Pair(psiElement.parents.firstIsInstanceOrNull<KtClassOrObject>() as? KtClass, if (varExpected) ValVar.VAR else ValVar.VAL)
    }
    enum class ValVar { VAL, VAR, NONE }
    val toxicPill = Pair(null, ValVar.NONE) // means do not check above this psi element, it's no use
    // todo: skip lambdas for now because Change Signature doesn't apply to them yet
    fun chooseContainerPreferringClass(element: PsiElement, varExpected: Boolean): Pair<PsiElement?, ValVar> {
        return element.parents
            .map {
                when {
                    (it is KtNamedFunction || it is KtSecondaryConstructor) && varExpected || it is KtPropertyAccessor -> chooseContainingClass(it, varExpected)
                    it is KtAnonymousInitializer -> Pair(it.parents.match(KtClassBody::class, last = KtClass::class), ValVar.NONE)
                    it is KtSuperTypeListEntry -> {
                        val klass = it.getStrictParentOfType<KtClassOrObject>()
                        if (klass is KtClass && klass.isInterface() || klass is KtEnumEntry) toxicPill else // couldn't add param to enum entry or interface
                        Pair(if (klass is KtClass) klass else null, ValVar.NONE)
                    }
                    it is KtClassBody -> {
                        val klass = it.parent as? KtClass
                        when {
                            klass is KtEnumEntry -> chooseContainingClass(klass, varExpected)
                            klass != null && klass.isInterface() -> Pair(null, ValVar.NONE)
                            else -> Pair(klass, ValVar.NONE)
                        }
                    }
                    it is KtNamedFunction || it is KtSecondaryConstructor -> Pair(it, ValVar.NONE)
                    it is KtObjectDeclaration && it.isCompanion() -> toxicPill // no introductions above companion object
                    else -> Pair(null, ValVar.NONE)
                }
            }
            .filter {
                it === toxicPill || it.first != null
            }
            .firstOrNull()
            ?: Pair(null, ValVar.NONE)
    }
}