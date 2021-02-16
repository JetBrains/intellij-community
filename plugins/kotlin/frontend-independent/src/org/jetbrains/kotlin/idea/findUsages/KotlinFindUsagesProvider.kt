// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.java.JavaFindUsagesProvider
import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter

open class KotlinFindUsagesProviderBase : FindUsagesProvider {
    private val javaProvider by lazy { JavaFindUsagesProvider() }

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean =
        psiElement is KtNamedDeclaration

    override fun getWordsScanner(): WordsScanner? = KotlinWordsScanner()

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String {
        return when (element) {
            is KtNamedFunction -> KotlinBundle.message("find.usages.function")
            is KtClass -> KotlinBundle.message("find.usages.class")
            is KtParameter -> KotlinBundle.message("find.usages.parameter")
            is KtProperty -> if (element.isLocal)
                KotlinBundle.message("find.usages.variable")
            else
                KotlinBundle.message("find.usages.property")
            is KtDestructuringDeclarationEntry -> KotlinBundle.message("find.usages.variable")
            is KtTypeParameter -> KotlinBundle.message("find.usages.type.parameter")
            is KtSecondaryConstructor -> KotlinBundle.message("find.usages.constructor")
            is KtObjectDeclaration -> KotlinBundle.message("find.usages.object")
            else -> ""
        }
    }

    val KtDeclaration.containerDescription: String?
        get() {
            containingClassOrObject?.let { return getDescriptiveName(it) }
            (parent as? KtFile)?.parent?.let { return getDescriptiveName(it) }
            return null
        }

    override fun getDescriptiveName(element: PsiElement): String {
        return when (element) {
            is PsiDirectory, is PsiPackage, is PsiFile -> javaProvider.getDescriptiveName(element)
            is KtClassOrObject -> {
                if (element is KtObjectDeclaration && element.isObjectLiteral()) return KotlinBundle.message("usage.provider.text.unnamed")
                run {
                    @Suppress("HardCodedStringLiteral")
                    element.fqName?.asString()
                } ?: element.name ?: KotlinBundle.message("usage.provider.text.unnamed")
            }
            is KtProperty -> {
                val name = element.name ?: ""
                element.containerDescription?.let { KotlinBundle.message("usage.provider.text.property.of.0", name, it) } ?: name
            }
            is KtFunction -> {
                //TODO: Correct FIR implementation
                return element.name?.let { "$it(...)" } ?: ""
            }
            is KtLabeledExpression -> {
                @Suppress("HardCodedStringLiteral")
                element.getLabelName() ?: ""
            }
            is KtImportAlias -> element.name ?: ""
            is KtLightElement<*, *> -> element.kotlinOrigin?.let { getDescriptiveName(it) } ?: ""
            is KtParameter -> {
                if (element.isPropertyParameter()) {
                    val name = element.name ?: ""
                    element.containerDescription?.let { KotlinBundle.message("usage.provider.text.property.of.0", name, it) } ?: name
                } else {
                    element.name ?: ""
                }
            }
            is PsiNamedElement -> element.name ?: ""
            else -> ""
        }
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        getDescriptiveName(element)
}
