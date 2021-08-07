// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry

abstract class KtDestructuringDeclarationReference(
    element: KtDestructuringDeclarationEntry
) : KtMultiReference<KtDestructuringDeclarationEntry>(element) {

    override fun getRangeInElement() = TextRange(0, element.textLength)

    abstract override fun canRename(): Boolean

    override fun handleElementRename(newElementName: String): PsiElement? {
        if (canRename()) return expression
        throw IncorrectOperationException()
    }

    override val resolvesByNames: Collection<Name>
        get() {
            val destructuringParent = element.parent as? KtDestructuringDeclaration ?: return emptyList()
            val componentIndex = destructuringParent.entries.indexOf(element) + 1
            return listOf(Name.identifier("component$componentIndex"))
        }
}
