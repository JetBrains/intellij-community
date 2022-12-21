// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.structureView

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.psi.callableIdIfNotLocal
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.codeInsight.SuperDeclaration
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinGoToSuperDeclarationsHandler
import org.jetbrains.kotlin.psi.*

abstract class AbstractKotlinGoToSuperDeclarationsHandlerTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FIR_PLUGIN

    protected fun performTest() {
        myFixture.configureAdditionalJavaFile()
        val file = myFixture.configureByDefaultFile() as KtFile
        val element = file.findElementAt(editor.caretModel.offset)
        var declaration =
            PsiTreeUtil.getParentOfType<KtDeclaration>(element, *KotlinGoToSuperDeclarationsHandler.ALLOWED_DECLARATION_CLASSES)
        if (declaration is KtParameter && !declaration.hasValOrVar()) {
            declaration = PsiTreeUtil.getParentOfType<KtDeclaration>(element, *KotlinGoToSuperDeclarationsHandler.ALLOWED_DECLARATION_CLASSES)
        }
        val superDeclarations = declaration?.let { KotlinGoToSuperDeclarationsHandler.findSuperDeclarations(it)}
        val actualText = render(superDeclarations?.items ?: emptyList())
        checkTextByExpectedPath(".expected", actualText)
    }

    private fun render(superDeclarations: List<SuperDeclaration>): String {
        return buildString {
            for (superDeclaration in superDeclarations) {
                val description = when (val declaration = superDeclaration.declaration.element) {
                    is KtClassOrObject -> declaration.classIdIfNonLocal?.toString()
                    is KtCallableDeclaration -> declaration.callableIdIfNotLocal?.toString()
                    is PsiNamedElement -> declaration.toString()
                    else -> declaration?.javaClass?.simpleName
                }
                appendLine(superDeclaration::class.simpleName + ": " + (description ?: "null"))
            }
        }
    }
}