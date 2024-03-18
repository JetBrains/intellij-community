/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.fir.analysis.providers

import com.intellij.openapi.components.service
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.providers.KotlinAnnotationsResolver
import org.jetbrains.kotlin.analysis.providers.KotlinAnnotationsResolverFactory
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import java.io.File

abstract class AbstractIdeKotlinAnnotationsResolverTest : KotlinLightCodeInsightFixtureTestCase() {
    private val expectedAnnotationsDirective: String = "// ANNOTATION:"

    override fun isFirPlugin(): Boolean = true

    private val annotationsResolver: KotlinAnnotationsResolver
        get() = project.service<KotlinAnnotationsResolverFactory>().createAnnotationResolver(GlobalSearchScope.projectScope(project))

    fun doTest(path: String) {
        val mainTestFile = File(path)
        val allTestFiles = listOf(mainTestFile) + resolveDependencyFiles(mainTestFile)

        myFixture.configureByFiles(*allTestFiles.toTypedArray())

        val annotatedElement = myFixture.elementAtCaret.parentOfType<KtAnnotated>(withSelf = true)
            ?: error("Expected KtAnnotation element at the caret: ${myFixture.elementAtCaret.getElementTextWithContext()}")

        val actualAnnotations = annotationsResolver.annotationsOnDeclaration(annotatedElement).sortedBy { it.toString() }
        val expectedAnnotations = InTextDirectivesUtils.findListWithPrefixes(
            myFixture.editor.document.text,
            expectedAnnotationsDirective
        ).filter { it.isNotEmpty() }

        assertEquals(
            "Expected annotations not found on the element under the caret:",
            expectedAnnotations,
            actualAnnotations.map { it.toString() }
        )
    }

    private fun resolveDependencyFiles(mainFile: File): List<File> {
        val dependencySuffixes = listOf(".dependency.kt", ".dependency1.kt", ".dependency2.kt")

        return dependencySuffixes
            .map { suffix -> mainFile.resolveSiblingWithDifferentExtension(suffix) }
            .filter { it.exists() }
    }

    private fun File.resolveSiblingWithDifferentExtension(newExtension: String): File =
        resolveSibling("$nameWithoutExtension$newExtension")
}

