// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.sessions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Assert
import java.io.File

/**
 * [CyclicDependenciesSymbolResolutionTest] ensures that sessions with cyclic dependencies can not only be created (which
 * [AbstractLocalSessionInvalidationTest] already ensures), but also that these sessions' dependency symbol providers don't run into any
 * stack overflow errors.
 *
 * A corresponding (or replacement) test on the Analysis API side does not exist yet, because the Analysis API test infrastructure does not
 * support cyclic module dependencies, which makes this strictly an issue of JPS project structures.
 */
class CyclicDependenciesSymbolResolutionTest : AbstractMultiModuleTest() {

    override fun getTestDataDirectory(): File = error("Should not be called")

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun `test that function symbols from cyclic dependencies can be resolved`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("a.kt", "fun foo(): Int = bar()")
            )
        }
        val moduleB = createModuleInTmpDir("b") {
            listOf(
                FileWithText("b.kt", "fun bar(): Int = foo()")
            )
        }

        moduleA.addKotlinStdlib()
        moduleB.addKotlinStdlib()

        moduleA.addDependency(moduleB)
        moduleB.addDependency(moduleA)

        val fileA = moduleA.findSourceKtFile("a.kt")
        analyzeInModalWindow(fileA, "test") {
            fileA.getFirstFunctionDeclaration().getBodyCallExpression().assertCalleeNameAndType(fileA, "bar", builtinTypes.int)
        }

        val fileB = moduleB.findSourceKtFile("b.kt")
        analyzeInModalWindow(fileB, "test") {
            fileB.getFirstFunctionDeclaration().getBodyCallExpression().assertCalleeNameAndType(fileB, "foo", builtinTypes.int)
        }
    }

    private fun KtFile.getFirstFunctionDeclaration(): KtNamedFunction = declarations.firstIsInstance<KtNamedFunction>()

    private fun KtNamedFunction.getBodyCallExpression(): KtCallExpression = bodyExpression!! as KtCallExpression

    context(_: KaSession)
    private fun KtCallExpression.assertCalleeNameAndType(file: KtFile, expectedName: String, expectedType: KaType) {
        val ktFunction = file.declarations.first() as KtNamedFunction
        val ktCall = ktFunction.bodyExpression!! as KtCallExpression

        val ktCallee = ktCall.calleeExpression as KtSimpleNameExpression
        Assert.assertEquals(expectedName, ktCallee.getReferencedName())

        val callableSymbol = ktCall.resolveToCall()!!.successfulFunctionCallOrNull()!!.symbol
        Assert.assertEquals(expectedType, callableSymbol.returnType)
    }
}
