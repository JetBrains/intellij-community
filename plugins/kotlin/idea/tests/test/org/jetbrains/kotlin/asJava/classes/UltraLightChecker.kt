// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.openapi.util.Conditions
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.PairProcessor
import com.intellij.util.ref.DebugReflectionUtil
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupportBase
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.asJava.PsiClassRenderer
import org.jetbrains.kotlin.idea.asJava.renderClass
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.junit.Assert
import java.io.File
import kotlin.test.fail

object UltraLightChecker {
    fun checkClassEquivalence(file: KtFile) {
        for (ktClass in allClasses(file)) {
            checkClassEquivalence(ktClass)
        }
    }

    fun allClasses(file: KtFile): List<KtClassOrObject> =
        SyntaxTraverser.psiTraverser(file).filter(KtClassOrObject::class.java).toList()

    fun checkByJavaFile(testDataPath: String, lightClasses: List<PsiClass>) {
        val expectedTextFile = getJavaFileForTest(testDataPath)
        val renderedResult = renderLightClasses(
            testDataPath,
            lightClasses.sortedBy { it.qualifiedName ?: it.name.toString() },
        )

        KotlinTestUtils.assertEqualsToFile(expectedTextFile, renderedResult)
    }

    fun getJavaFileForTest(testDataFile: File, suffixes: List<String> = listOf("descriptors")): File {
        val expectedTextFile = suffixes.firstNotNullOfOrNull {
            KotlinTestUtils.replaceExtension(testDataFile, "$it.java").takeIf(File::exists)
        } ?: KotlinTestUtils.replaceExtension(testDataFile, "java")

        KotlinLightCodeInsightFixtureTestCaseBase.assertTrue(expectedTextFile.exists())
        return expectedTextFile
    }

    fun getJavaFileForTest(testDataPath: String): File = getJavaFileForTest(File(testDataPath))

    fun renderLightClasses(testDataPath: String, lightClasses: List<PsiClass>): String {
        val extendedTypeRendererOld = PsiClassRenderer.extendedTypeRenderer
        return try {
            PsiClassRenderer.extendedTypeRenderer = testDataPath.endsWith("typeAnnotations.kt")
            lightClasses.sortedBy { it.name }.joinToString("\n\n") { it.renderClass() }
        } finally {
            PsiClassRenderer.extendedTypeRenderer = extendedTypeRendererOld
        }
    }

    fun checkClassEquivalence(ktClass: KtClassOrObject): PsiClass? {
        val javaSupport = KotlinAsJavaSupport.getInstance(ktClass.project).cast<KotlinAsJavaSupportBase<*>>()
        val ultraLightClass = javaSupport.createLightClass(ktClass)?.value ?: return null
        val secondULInstance = javaSupport.createLightClass(ktClass)?.value
        Assert.assertNotNull(secondULInstance)
        Assert.assertTrue(ultraLightClass !== secondULInstance)
        secondULInstance!!
        Assert.assertEquals(ultraLightClass.methods.size, secondULInstance.methods.size)
        Assert.assertTrue(ultraLightClass.methods.asList().containsAll(secondULInstance.methods.asList()))

        return ultraLightClass
    }

    private fun checkDescriptorLeakOnElement(element: PsiElement) {
        DebugReflectionUtil.walkObjects(
            10,
            mapOf(element to element.javaClass.name),
            DeclarationDescriptor::class.java,
            Conditions.alwaysTrue<Any>()::value,
            PairProcessor { value, backLink ->
                fail("Leaked descriptor ${value.javaClass.name} in ${element.javaClass.name}\n$backLink")
            }
        )
    }

    fun checkDescriptorsLeak(lightClass: PsiClass) {
        checkDescriptorLeakOnElement(lightClass)
        lightClass.methods.forEach {
            checkDescriptorLeakOnElement(it)
            it.parameterList.parameters.forEach { parameter -> checkDescriptorLeakOnElement(parameter) }
        }
        lightClass.fields.forEach { checkDescriptorLeakOnElement(it) }
    }
}
