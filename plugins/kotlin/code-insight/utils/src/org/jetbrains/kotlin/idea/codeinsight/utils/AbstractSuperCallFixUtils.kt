// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperExpression

object AbstractSuperCallFixUtils {

    fun KtSuperExpression.specifySuperType(superType: FqName) {
        val label = labelQualifier?.text ?: ""
        val replaced = replaced(KtPsiFactory(project).createExpression("super<${superType.asString()}>$label"))
        ShortenReferencesFacility.getInstance().shorten(replaced)
    }

    fun KtClassOrObject.addSuperTypeListEntryIfNotExists(superType: FqName) {
        val superTypeFullName = superType.asString()
        val superTypeShortName = superType.shortName().asString()
        val superTypeNames = setOf(superTypeShortName, superTypeFullName)
        val superTypeListEntry = superTypeListEntries.firstOrNull { it.text in superTypeNames }
        if (superTypeListEntry == null) {
            val added = addSuperTypeListEntry(KtPsiFactory(this.project).createSuperTypeEntry(superTypeFullName))
            ShortenReferencesFacility.getInstance().shorten(added)
        } else if (superTypeListEntry.text == superTypeFullName) {
            ShortenReferencesFacility.getInstance().shorten(superTypeListEntry)
        }
    }

    fun KtNameReferenceExpression.getParentSuperExpression(): KtSuperExpression? =
        parentOfType<KtDotQualifiedExpression>()?.receiverExpression as? KtSuperExpression
}
