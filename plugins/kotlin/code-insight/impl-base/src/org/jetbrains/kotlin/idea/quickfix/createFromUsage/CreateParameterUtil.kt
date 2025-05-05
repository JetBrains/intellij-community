// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.createFromUsage

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
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
    private val toxicPill: Pair<Nothing?, ValVar> = Pair(null, ValVar.NONE) // means do not check above this psi element, it's no use
    // todo: skip lambdas for now because Change Signature doesn't apply to them yet
    fun chooseContainerPreferringClass(element: PsiElement, varExpected: Boolean): Pair<PsiElement?, ValVar> {
        return chooseContainers(element, varExpected)
            .firstOrNull()
            ?: Pair(null, ValVar.NONE)
    }

    fun chooseContainers(element: PsiElement, varExpected: Boolean): Sequence<Pair<KtDeclaration?, ValVar>> {
        val primaryParametersAccessible = PsiTreeUtil.getParentOfType(
            element,
            KtClassBody::class.java,
            true,
            KtClassOrObject::class.java,
            KtNamedFunction::class.java,
            KtSecondaryConstructor::class.java
        ) != null

        return element.parents
            .map {
                when {
                    (it is KtNamedFunction || it is KtSecondaryConstructor) && varExpected || it is KtPropertyAccessor -> chooseContainingClass(
                        it,
                        varExpected
                    )

                    it is KtPrimaryConstructor -> Pair(it, if (varExpected) ValVar.VAR else ValVar.VAL)

                    it is KtAnonymousInitializer -> Pair(it.parents.match(KtClassBody::class, last = KtClass::class), ValVar.NONE)
                    it is KtSuperTypeListEntry -> {
                        val klass = it.getStrictParentOfType<KtClassOrObject>()
                        if (klass is KtClass && klass.isInterface() || klass is KtEnumEntry) toxicPill else // couldn't add param to enum entry or interface
                            Pair(if (klass is KtClass) klass else null, ValVar.NONE)
                    }

                    it is KtConstructorDelegationCall -> {
                        Pair(it.getStrictParentOfType<KtClassOrObject>(), ValVar.NONE)
                    }

                    it is KtClassBody -> {
                        val klass = it.parent as? KtClassOrObject
                        when {
                            klass is KtEnumEntry -> chooseContainingClass(klass, varExpected)
                            klass is KtClass && klass.isInterface() -> Pair(null, ValVar.NONE)
                            klass is KtObjectDeclaration -> toxicPill
                            else -> Pair(klass, if (primaryParametersAccessible) ValVar.NONE else ValVar.VAL)
                        }
                    }

                    it is KtNamedFunction || it is KtSecondaryConstructor -> Pair(it, ValVar.NONE)
                    it is KtObjectDeclaration && it.isCompanion() -> toxicPill // no introductions above companion object
                    else -> Pair(null, ValVar.NONE)
                }
            }
            .takeWhile { it !== toxicPill }
            .filter {
                it.first != null
            }
    }
}