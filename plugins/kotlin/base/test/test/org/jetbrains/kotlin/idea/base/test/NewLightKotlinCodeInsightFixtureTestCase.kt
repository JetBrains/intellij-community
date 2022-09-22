// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.plugin.checkKotlinPluginKind
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
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.io.path.writeText

abstract class NewLightKotlinCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {
    protected abstract val pluginKind: KotlinPluginKind

    val testRoot: Path by lazy { KotlinTestHelpers.getTestRootPath(javaClass) }

    override fun getTestDataPath(): String {
        val pathString = testRoot.absolutePathString()
        return if (pathString.endsWith(File.separatorChar)) pathString else pathString + File.separatorChar
    }

    protected open val directivesContainer: DirectivesContainer
        get() = LanguageSettingsDirectives

    private var cachedDirectives: RegisteredDirectives? = null

    protected val directives: RegisteredDirectives by lazy {
        val directiveParser = RegisteredDirectivesParser(directivesContainer, JUnit4Assertions)
        myFixture.file.text.lineSequence().forEach(directiveParser::parse)
        directiveParser.build()
    }

    protected val mainPath: Path by lazy {
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

        val testClassPath = testClass.getAnnotation(TestMetadata::class.java)?.value
            ?: error("@${TestMetadata::class.java} annotation not found on class '${testClass.name}'")

        val testMethodPath = testMethod.getAnnotation(TestMetadata::class.java)?.value
            ?: error("@${TestMetadata::class.java} annotation not found on method '${testMethod.name}'")

        Paths.get(testClassPath, testMethodPath)
    }

    protected val mainPathAbsolute: Path
        get() = Paths.get(testDataPath).resolve(mainPath)

    override fun setUp() {
        val isK2Plugin = pluginKind == KotlinPluginKind.FIR_PLUGIN
        System.setProperty("idea.kotlin.plugin.use.k2", isK2Plugin.toString())
        super.setUp()
        checkKotlinPluginKind(pluginKind)
    }

    override fun tearDown() {
        cachedDirectives = null
        super.tearDown()
    }

    fun checkTextByExpectedPath(expectedSuffix: String, actual: String) {
        val expectedPath = KotlinTestHelpers.getExpectedPath(mainPath, expectedSuffix)
        KotlinTestHelpers.assertEqualsToPath(expectedPath, actual)
    }

    fun JavaCodeInsightTestFixture.configureByMainPath(): PsiFile {
        return configureByFile(mainPath.toString())
    }

    fun JavaCodeInsightTestFixture.configureByMainPathStrippingTags(vararg tagNames: String): PsiFile {
        val text = mainPath.readText()
        return configureByText(mainPath.name, KotlinTestHelpers.stripTags(text, *tagNames))
    }

    fun JavaCodeInsightTestFixture.checkContentByExpectedPath(expectedSuffix: String) {
        val expectedPath = KotlinTestHelpers.getExpectedPath(mainPath, expectedSuffix)
        try {
            checkResultByFile(expectedPath.toString(), /* ignoreTrailingWhitespaces = */ true)
        } catch (e: RuntimeException) {
            if (e.cause is FileNotFoundException) {
                val absoluteExpectedPath = Paths.get(testDataPath).resolve(expectedPath)
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
}