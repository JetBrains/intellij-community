// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.plugin.checkKotlinPluginKind
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.services.impl.RegisteredDirectivesParser
import java.io.File
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

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

    fun JavaCodeInsightTestFixture.configureByMainPath(): PsiFile {
        return configureByFile(mainPath.toString())
    }

    fun JavaCodeInsightTestFixture.checkResultByExpectedPath(expectedSuffix: String) {
        val expectedPath = KotlinTestHelpers.getExpectedPath(mainPath, expectedSuffix)
        checkResultByFile(expectedPath.toString(), true)
    }
}