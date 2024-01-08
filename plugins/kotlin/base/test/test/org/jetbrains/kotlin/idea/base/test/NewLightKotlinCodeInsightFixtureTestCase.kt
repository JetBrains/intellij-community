// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.checkKotlinPluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.services.impl.RegisteredDirectivesParser
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

abstract class NewLightKotlinCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {
    protected abstract val pluginKind: KotlinPluginMode

    private val testRoot: String by lazy {
        val testClassPath = javaClass.getAnnotation(TestMetadata::class.java)?.value
        ?: error("@${TestMetadata::class.java} annotation not found on class '${javaClass.name}'")
        val pathString = KotlinTestHelpers.getTestRootPath(javaClass).resolve(testClassPath).absolutePathString()
        if (pathString.endsWith(File.separatorChar)) pathString else pathString + File.separatorChar
    }

    override fun getTestDataPath(): String {
        return testRoot
    }

    protected open val directivesContainer: DirectivesContainer
        get() = LanguageSettingsDirectives

    private var cachedDirectives: RegisteredDirectives? = null

    protected val directives: RegisteredDirectives by lazy {
        val directiveParser = RegisteredDirectivesParser(directivesContainer, JUnit4Assertions)
        myFixture.file.text.lineSequence().forEach(directiveParser::parse)
        directiveParser.build()
    }

    protected val testRootPath: Path
        get() = Paths.get(testRoot)

    protected val testMethodPath: Path by lazy {
        val testName = this.name
        val testClass = javaClass
        val testMethod = testClass.methods
            .single { method ->
                Modifier.isPublic(method.modifiers)
                        && !Modifier.isStatic(method.modifiers)
                        && method.name == testName
                        && method.parameterCount == 0
                        && method.returnType == Void.TYPE
            }

        val testMethodPath = testMethod.getAnnotation(TestMetadata::class.java)?.value
            ?: error("@${TestMetadata::class.java} annotation not found on method '${testMethod.name}'")
        Paths.get(testMethodPath)
    }

    override fun setUp() {
        val isK2Plugin = pluginKind == KotlinPluginMode.K2
        System.setProperty("idea.kotlin.plugin.use.k2", isK2Plugin.toString())
        super.setUp()
        checkKotlinPluginMode(pluginKind)
    }

    override fun tearDown() {
        cachedDirectives = null
        super.tearDown()
    }

    fun checkTextByExpectedPath(expectedSuffix: String, actual: String) {
        val expectedPath = Paths.get(testDataPath, getExpectedPath(expectedSuffix))
        KotlinTestHelpers.assertEqualsToPath(expectedPath, actual)
    }

    fun JavaCodeInsightTestFixture.configureByDefaultFile(): PsiFile {
        return configureByFile(testMethodPath.toString())
    }

    fun JavaCodeInsightTestFixture.configureAdditionalJavaFile(): PsiFile? {
        val testFilePath = testMethodPath.toString()
        if (testFilePath.contains("withJava")) {
            return myFixture.configureByFile(testFilePath.replace("kt", "java"))
        }
        return null
    }

    fun JavaCodeInsightTestFixture.configureDependencies() {
        val dependencySuffixes = listOf(".dependency.kt", ".dependency.java", ".dependency1.kt", ".dependency2.kt")
        for (suffix in dependencySuffixes) {
            val dependencyPath = testMethodPath.toString().replace(".kt", suffix)
            if (File(testRoot, dependencyPath).exists()) {
                myFixture.configureByFile(dependencyPath)
            }
        }
    }

    fun JavaCodeInsightTestFixture.checkContentByExpectedPath(expectedSuffix: String, addSuffixAfterExtension: Boolean = false) {
        val expectedPathString = getExpectedPath(expectedSuffix, addSuffixAfterExtension)

        if (pluginKind == KotlinPluginMode.K2) {
            val expectedPath = Paths.get(testDataPath, expectedPathString)

            val k2ExpectedPathString = getExpectedPath(".fir" + expectedSuffix, addSuffixAfterExtension)
            val k2ExpectedPath = Paths.get(testDataPath, k2ExpectedPathString)

            if (k2ExpectedPath.exists()) {
                checkContentByExpectedPath(k2ExpectedPathString)
                IgnoreTests.cleanUpIdenticalFirTestFile(
                    originalTestFile = expectedPath.toFile(),
                    firTestFile = k2ExpectedPath.toFile()
                )

                return
            }
        }

        checkContentByExpectedPath(expectedPathString)
    }

    private fun JavaCodeInsightTestFixture.checkContentByExpectedPath(expectedPathString: String) {
        try {
            checkResultByFile(expectedPathString, /* ignoreTrailingWhitespaces = */ true)
        } catch (e: RuntimeException) {
            if (e.cause is FileNotFoundException) {
                val absoluteExpectedPath = Paths.get(testDataPath).resolve(expectedPathString)
                if (!absoluteExpectedPath.exists()) {
                    val mainFile = file
                    val originalVirtualFile = when (val virtualFile = mainFile.virtualFile) {
                        is VirtualFileWindow -> virtualFile.delegate
                        else -> virtualFile
                    }

                    val targetFile = runReadAction { psiManager.findFile(originalVirtualFile) } ?: mainFile

                    absoluteExpectedPath.writeText(targetFile.text)
                    TestCase.fail("Expected file didn't exist. New file was created (${absoluteExpectedPath.absolutePathString()}).")
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    private fun getExpectedPath(expectedSuffix: String, addSuffixAfterExtension: Boolean = false): String = buildString {
        append(testMethodPath.nameWithoutExtension)
        if (!addSuffixAfterExtension) append(expectedSuffix)
        append(".")
        append(testMethodPath.extension)
        if (addSuffixAfterExtension) append(expectedSuffix)
    }
}