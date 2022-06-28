// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.perf

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Conditions
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.PairProcessor
import com.intellij.util.ref.DebugReflectionUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.asJava.PsiClassRenderer
import org.jetbrains.kotlin.idea.asJava.renderClass
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import java.io.File

object UltraLightChecker {
    fun checkClassEquivalence(file: KtFile) {
        for (ktClass in allClasses(file)) {
            checkClassEquivalence(ktClass)
        }
    }

    fun checkForReleaseCoroutine(sourceFileText: String, module: Module) {
        if (sourceFileText.contains("//RELEASE_COROUTINE_NEEDED")) {
            TestCase.assertTrue(
                "Test should be runned under language version that supports released coroutines",
                module.languageVersionSettings.supportsFeature(LanguageFeature.ReleaseCoroutines)
            )
        }
    }

    fun allClasses(file: KtFile): List<KtClassOrObject> =
        SyntaxTraverser.psiTraverser(file).filter(KtClassOrObject::class.java).toList()

    fun checkByJavaFile(testDataPath: String, lightClasses: List<KtLightClass>) {
        val expectedTextFile = getJavaFileForTest(testDataPath)
        val renderedResult = renderLightClasses(testDataPath, lightClasses)
        KotlinTestUtils.assertEqualsToFile(expectedTextFile, renderedResult)
    }

    fun getJavaFileForTest(testDataPath: String): File {
        val expectedTextFile = KotlinTestUtils.replaceExtension(File(testDataPath), "java")
        KotlinLightCodeInsightFixtureTestCaseBase.assertTrue(expectedTextFile.exists())
        return expectedTextFile
    }

    fun renderLightClasses(testDataPath: String, lightClasses: List<KtLightClass>): String {
        val extendedTypeRendererOld = PsiClassRenderer.extendedTypeRenderer
        return try {
            PsiClassRenderer.extendedTypeRenderer = testDataPath.endsWith("typeAnnotations.kt")
            lightClasses.joinToString("\n\n") { it.renderClass() }
        } finally {
            PsiClassRenderer.extendedTypeRenderer = extendedTypeRendererOld
        }
    }

    fun checkClassEquivalence(ktClass: KtClassOrObject): KtUltraLightClass? {
        val ultraLightClass = LightClassGenerationSupport.getInstance(ktClass.project).createUltraLightClass(ktClass) ?: return null
        val secondULInstance = LightClassGenerationSupport.getInstance(ktClass.project).createUltraLightClass(ktClass)
        Assert.assertNotNull(secondULInstance)
        Assert.assertTrue(ultraLightClass !== secondULInstance)
        secondULInstance!!
        Assert.assertEquals(ultraLightClass.ownMethods.size, secondULInstance.ownMethods.size)
        Assert.assertTrue(ultraLightClass.ownMethods.containsAll(secondULInstance.ownMethods))

        return ultraLightClass
    }

    private fun checkDescriptorLeakOnElement(element: PsiElement) {
        DebugReflectionUtil.walkObjects(
            10,
            mapOf(element to element.javaClass.name),
            Any::class.java,
            Conditions.alwaysTrue<Any>()::value,
            PairProcessor.alwaysTrue(),
        )
    }

    fun checkDescriptorsLeak(lightClass: KtLightClass) {
        checkDescriptorLeakOnElement(lightClass)
        lightClass.methods.forEach {
            checkDescriptorLeakOnElement(it)
            it.parameterList.parameters.forEach { parameter -> checkDescriptorLeakOnElement(parameter) }
        }
        lightClass.fields.forEach { checkDescriptorLeakOnElement(it) }
    }
}
