// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.PlainTextSymbolCompletionContributor
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

private val separators = listOf(".", "#", "::")

class KotlinPlainTextSymbolCompletionContributor : PlainTextSymbolCompletionContributor {
    override fun getLookupElements(file: PsiFile, invocationCount: Int, prefix: String): Collection<LookupElement> {
        val ktFile = file as? KtFile ?: return emptyList()
        val result = mutableListOf<LookupElement>()
        for (declaration in ktFile.declarations) {
            val name = declaration.name ?: continue
            result += LookupElementBuilder.create(name).withIcon(declaration.getIcon(0))
            val infix = getInfix(prefix, name)
            var memberPrefix: String? = null
            val rest: String
            if (infix != null) {
                val offset = name.length + infix.length
                memberPrefix = prefix.substring(0, offset)
                rest = prefix.substring(offset)
            } else {
                if (invocationCount <= 0) continue
                rest = ""
            }
            if (declaration is KtClassOrObject) {
                processClassBody(invocationCount, result, declaration, infix, memberPrefix, rest)
            }
        }
        return result
    }

    private fun processClassBody(
        invocationCount: Int,
        result: MutableList<LookupElement>,
        aClass: KtClassOrObject,
        infix: String?,
        memberPrefix: String?,
        rest: String
    ) {
        val members = aClass.declarations + aClass.primaryConstructorParameters.filter { it.hasValOrVar() }
        for (member in members) {
            val memberName = member.name ?: continue
            val icon = member.getIcon(0)
            val element = LookupElementBuilder.create(memberName).withIcon(icon)
            if (invocationCount > 0) {
                result += element
            }
            if (memberPrefix == null || (member is KtClassOrObject && infix != ".")) continue
            result += LookupElementBuilder.create(memberPrefix + memberName).withIcon(icon)
            if (member !is KtClassOrObject) continue
            val nestedInfix = getInfix(rest, memberName) ?: continue
            val index = memberName.length + nestedInfix.length
            val nestedPrefix = memberPrefix + rest.substring(0, index)
            processClassBody(0, result, member, nestedInfix, nestedPrefix, rest.substring(index))
        }
    }

    private fun getInfix(currentPrefix: String, className: String): String? {
        if (!currentPrefix.startsWith(className)) return null
        return separators.firstOrNull { currentPrefix.startsWith(it, className.length) }
    }
}
