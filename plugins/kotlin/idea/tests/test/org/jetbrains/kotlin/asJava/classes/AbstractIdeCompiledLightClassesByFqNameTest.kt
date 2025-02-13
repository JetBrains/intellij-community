// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.asJava.classes

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.idea.asJava.PsiClassRenderer
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinMultiFileLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.junit.Assume

abstract class AbstractIdeCompiledLightClassesByFqNameTest : KotlinMultiFileLightCodeInsightFixtureTestCase() {
    override val isLibraryByDefault: Boolean get() = true

    override fun setUp() {
        val parsedDirectives = KotlinTestUtils.parseDirectives(dataFile().readText())
        Assume.assumeFalse(
            "The test is not supported",
            LightClassTestCommon.SKIP_IDE_TEST_DIRECTIVE in parsedDirectives || LightClassTestCommon.SKIP_LIBRARY_EXCEPTIONS in parsedDirectives,
        )

        super.setUp()
    }

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val testDataFile = dataFile()
        withCustomCompilerOptions(testDataFile.readText(), project, module) {
            testLightClass(
                testData = testDataFile,
                suffixes = listOf("compiled", "lib"),
                normalize = { it },
                findLightClass = {
                    findLightClass(it, null, project)?.apply {
                        PsiElementChecker.checkPsiElementStructure(this)
                    }
                },
                membersFilter = MembersFilterForCompiledClasses
            )
        }
    }

    private object MembersFilterForCompiledClasses : PsiClassRenderer.MembersFilter {
        override fun includeMethod(psiMethod: PsiMethod): Boolean {
            // Exclude methods for local functions.
            // JVM_IR generates local functions (and some lambdas) as private methods in the surrounding class.
            // Such methods are private and have names such as 'foo$...'.
            // They are not a part of the public API, and are not represented in the light classes.
            // NB this is a heuristic, and it will obviously fail for declarations such as 'private fun `foo$bar`() {}'.
            // However, it allows writing code in more or less "idiomatic" style in the light class tests
            // without thinking about private ABI and compiler optimizations.
            if (psiMethod.modifierList.hasExplicitModifier(PsiModifier.PRIVATE)) {
                return '$' !in psiMethod.name
            }
            return super.includeMethod(psiMethod)
        }
    }
}