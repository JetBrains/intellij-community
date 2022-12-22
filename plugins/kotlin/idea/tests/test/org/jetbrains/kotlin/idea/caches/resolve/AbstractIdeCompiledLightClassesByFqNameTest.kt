// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.idea.KotlinDaemonAnalyzerTestCase
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.asJava.PsiClassRenderer
import org.jetbrains.kotlin.idea.test.*
import java.io.File

abstract class AbstractIdeCompiledLightClassTest : KotlinDaemonAnalyzerTestCase() {
    override fun setUp() {
        super.setUp()

        val testName = getTestName(false)

        val testDataDir = TestMetadataUtil.getTestData(this::class.java)
        val testFile = listOf(File(testDataDir, "$testName.kt"), File(testDataDir, "$testName.kts")).first { it.exists() }

        val extraClasspath = mutableListOf(KotlinArtifacts.instance.jetbrainsAnnotations, KotlinArtifacts.instance.kotlinStdlibJdk8)
        if (testFile.extension == "kts") {
            extraClasspath += KotlinArtifacts.instance.kotlinScriptRuntime
        }

        val extraOptions = KotlinTestUtils.parseDirectives(testFile.readText())[
            CompilerTestDirectives.JVM_TARGET_DIRECTIVE.substringBefore(":")
        ]?.let { jvmTarget ->
            listOf("-jvm-target", jvmTarget)
        } ?: emptyList()

        val libraryJar = KotlinCompilerStandalone(
            listOf(testFile),
            classpath = extraClasspath,
            options = extraOptions
        ).compile()

        val jarUrl = "jar://" + FileUtilRt.toSystemIndependentName(libraryJar.absolutePath) + "!/"
        ModuleRootModificationUtil.addModuleLibrary(module, jarUrl)
    }

    fun doTest(testDataPath: String) {
        val testDataFile = File(testDataPath)
        val expectedFile = KotlinTestUtils.replaceExtension(testDataFile, "compiled.java").let {
            if (it.exists()) it else KotlinTestUtils.replaceExtension(testDataFile, "java")
        }
        withCustomCompilerOptions(testDataFile.readText(), project, module) {
            testLightClass(
                expectedFile,
                testDataFile,
                { it },
                {
                    findClass(it, null, project)?.apply {
                        PsiElementChecker.checkPsiElementStructure(this)
                    }
                },
                MembersFilterForCompiledClasses
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