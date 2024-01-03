// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.structureView

import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.psi.callableIdIfNotLocal
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.codeInsight.SuperDeclaration
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinGoToSuperDeclarationsHandler
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractKotlinGoToSuperDeclarationsHandlerTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    protected fun performTest() {
        myFixture.configureAdditionalJavaFile()
        val file = myFixture.configureByDefaultFile() as KtFile
        val declaration = KotlinGoToSuperDeclarationsHandler.findTargetDeclaration(file, editor)
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