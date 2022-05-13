// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.PlainTextSymbolCompletionContributor
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty

class KotlinPlainTextSymbolCompletionContributor : PlainTextSymbolCompletionContributor {
    override fun getLookupElements(file: PsiFile, invocationCount: Int, prefix: String): Collection<LookupElement> {
        val ktFile = file as? KtFile ?: return emptyList()
        val result: MutableList<LookupElement> = ArrayList()
        for (declaration in ktFile.declarations) {
            val name = declaration.name ?: continue
            result.add(LookupElementBuilder.create(name).withIcon(declaration.getIcon(0)))
            val infix = getInfix(prefix, name)
            var memberPrefix: String? = null
            var rest: String? = null
            if (infix != null) {
                val offset = name.length + infix.length
                memberPrefix = prefix.substring(0, offset)
                rest = prefix.substring(offset)
            } else if (invocationCount <= 0) continue
            if (declaration is KtClass) {
                processClassBody(invocationCount, result, declaration, infix, memberPrefix, rest ?: "")
            }
        }
        return result
    }

    private fun processClassBody(
        invocationCount: Int,
        result: MutableList<LookupElement>,
        aClass: KtClass,
        infix: String?,
        memberPrefix: String?,
        rest: String
    ) {
        for (members in listOf(aClass.declarations, aClass.getPrimaryConstructorParameterList()?.parameters ?: listOf())) {
            for (member in members) {
                val memberName = member.name ?: continue
                val icon = member.getIcon(0)
                val element = LookupElementBuilder.create(memberName).withIcon(icon)
                if (invocationCount > 0) {
                    result.add(element)
                }
                if (memberPrefix != null) {
                    if (member is KtFunction || member is KtProperty && infix != "::" || infix == ".") {
                        result.add(LookupElementBuilder.create(memberPrefix + memberName).withIcon(icon))
                        if (member is KtClass) {
                            val nestedInfix = getInfix(rest, memberName)
                            if (nestedInfix != null) {
                                val index = memberName.length + nestedInfix.length
                                val nestedPrefix = memberPrefix + rest.substring(0, index)
                                processClassBody(0, result, member, nestedInfix, nestedPrefix, rest.substring(index))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getInfix(currentPrefix: String, className: String): String? {
        if (!currentPrefix.startsWith(className)) return null
        return arrayOf(".", "#", "::").firstOrNull { currentPrefix.startsWith(it, className.length) }
    }

}