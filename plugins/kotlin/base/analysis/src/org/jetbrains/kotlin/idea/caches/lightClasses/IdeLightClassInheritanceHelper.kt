// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.lightClasses

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.asJava.ImpreciseResolveResult
import org.jetbrains.kotlin.asJava.ImpreciseResolveResult.*
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.LightClassInheritanceHelper
import org.jetbrains.kotlin.asJava.classes.defaultJavaAncestorQualifiedName
import org.jetbrains.kotlin.idea.base.util.isInDumbMode
import org.jetbrains.kotlin.idea.search.PsiBasedClassResolver
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry

class IdeLightClassInheritanceHelper : LightClassInheritanceHelper {
    override fun isInheritor(
        lightClass: KtLightClass,
        baseClass: PsiClass,
        checkDeep: Boolean,
    ): ImpreciseResolveResult {
        if (baseClass.project.isInDumbMode) return NO_MATCH
        if (lightClass.manager.areElementsEquivalent(baseClass, lightClass)) return NO_MATCH

        val classOrObject = lightClass.kotlinOrigin ?: return UNSURE

        if (checkDeep && baseClass.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT) {
            return MATCH
        }

        val entries = classOrObject.superTypeListEntries
        if (baseClass.qualifiedName == classOrObject.defaultJavaAncestorQualifiedName() && (checkDeep || entries.none { it is KtSuperTypeCallEntry })) {
            return MATCH
        }

        val amongEntries = isAmongEntries(baseClass, entries)
        return when {
            !checkDeep -> amongEntries
            amongEntries == MATCH -> MATCH
            else -> UNSURE
        }
    }

    private fun isAmongEntries(baseClass: PsiClass, entries: List<KtSuperTypeListEntry>): ImpreciseResolveResult {
        val psiBasedResolver = PsiBasedClassResolver.getInstance(baseClass)
        for (entry in entries) {
            val reference: KtSimpleNameExpression = entry.typeAsUserType?.referenceExpression ?: continue
            when (psiBasedResolver.canBeTargetReference(reference)) {
                MATCH -> return MATCH
                NO_MATCH -> continue
                UNSURE -> return UNSURE
            }
        }

        return NO_MATCH
    }
}
