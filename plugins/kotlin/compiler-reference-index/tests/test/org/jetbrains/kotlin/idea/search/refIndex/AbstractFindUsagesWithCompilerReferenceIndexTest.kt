// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest
import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest.Companion.FindUsageTestType
import org.jetbrains.kotlin.findUsages.KotlinFindUsageConfigurator
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.readText

abstract class AbstractFindUsagesWithCompilerReferenceIndexTest : KotlinCompilerReferenceTestBase() {
    override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
        super.tuneFixture(moduleBuilder)
        moduleBuilder.setLanguageLevel(LanguageLevel.JDK_17)
        moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
    }

    protected open val ignoreLog: Boolean get() = false
    override val withK2Compiler: Boolean get() = false

    override fun getTestDataPath(): String = File(TestMetadataUtil.getTestDataPath(javaClass)).path

    protected fun doTest(path: String) {
        val isFir = pluginMode == KotlinPluginMode.K2
        val criType = if (isFir) FindUsageTestType.FIR_CRI else FindUsageTestType.CRI
        runCatching {
            AbstractFindUsagesTest.doFindUsageTest<PsiElement>(
                path,
                configurator = KotlinFindUsageConfigurator.fromFixture(myFixture),
                executionWrapper = { findUsageTest ->
                    findUsageTest(if (isFir) FindUsageTestType.FIR else FindUsageTestType.DEFAULT)

                    installCompiler()
                    rebuildProject()
                    findUsageTest(criType)
                },
                ignoreLog = ignoreLog,
                testPlatform = KMPTestPlatform.Unspecified,
                testType = criType,
            )
        }.fold(
            onSuccess = {
                if (isFir && shouldIgnore(path)) {
                    error("FIR_CRI_IGNORE directive is redundant")
                }
            },
            onFailure = {
                if (!isFir || !shouldIgnore(path)) {
                    throw it
                }
            },
        )
    }

    private fun shouldIgnore(path: String): Boolean {
        return !isCompatibleVersions || InTextDirectivesUtils.findStringWithPrefixes(Path(path).readText(), "// FIR_CRI_IGNORE:") != null
    }
}
