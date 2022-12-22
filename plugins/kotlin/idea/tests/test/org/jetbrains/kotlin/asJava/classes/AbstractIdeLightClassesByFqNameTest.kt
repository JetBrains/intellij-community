// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.asJava.elements.*
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.File
import kotlin.test.assertNotNull

abstract class AbstractIdeLightClassesByFqNameTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(@Suppress("UNUSED_PARAMETER") unused: String) {
        val fileName = fileName()
        val fileExtension = fileName.substringAfterLast('.')
        val extraFilePath = when {
            fileName.endsWith(fileExtension) -> fileName.replace(fileExtension, "extra.$fileExtension")
            else -> error("Invalid test data extension")
        }

        val fileText = File(testDataDirectory, fileName).readText()
        withCustomCompilerOptions(fileText, project, module) {
            val testFiles = if (File(testDataDirectory, extraFilePath).isFile) listOf(fileName, extraFilePath) else listOf(fileName)
            myFixture.configureByFiles(*testFiles.toTypedArray())
            if ((myFixture.file as? KtFile)?.isScript() == true) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
            }

            val ktFile = myFixture.file as KtFile
            val testData = dataFile()
            testLightClass(
                KotlinTestUtils.replaceExtension(testData, "java"),
                testData,
                { LightClassTestCommon.removeEmptyDefaultImpls(it) },
                { fqName ->
                    findLightClass(fqName, ktFile, project)?.apply {
                        checkConsistency(this as KtLightClass)
                        PsiElementChecker.checkPsiElementStructure(this)
                    }
                },
            )

            UltraLightChecker.checkClassEquivalence(ktFile)
        }
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithStdlibJdk8()
}

private fun checkConsistency(lightClass: KtLightClass) {
    checkModifierList(lightClass.modifierList!!)

    for (field in lightClass.fields) {
        field as KtLightField
        checkModifierList(field.modifierList!!)
        checkAnnotationConsistency(field)
    }

    for (method in lightClass.methods) {
        method as KtLightMethod
        checkModifierList(method.modifierList)
        checkAnnotationConsistency(method)
        method.parameterList.parameters.forEach {
            checkAnnotationConsistency(it as KtLightParameter)
        }
    }

    checkAnnotationConsistency(lightClass)

    lightClass.innerClasses.forEach { checkConsistency(it as KtLightClass) }
}

private fun checkAnnotationConsistency(modifierListOwner: KtLightElement<*, PsiModifierListOwner>) {
    if (modifierListOwner is KtLightClassForFacade) return

    val annotations = modifierListOwner.safeAs<PsiModifierListOwner>()?.modifierList?.annotations ?: return
    for (annotation in annotations) {
        if (annotation !is KtLightNullabilityAnnotation<*>)
            assertNotNull(
                annotation!!.nameReferenceElement,
                "nameReferenceElement should be not null for $annotation of ${annotation.javaClass}"
            )
    }
}

private fun checkModifierList(modifierList: PsiModifierList) {
    // see org.jetbrains.kotlin.asJava.elements.KtLightNonSourceAnnotation
    val isAnnotationClass = (modifierList.parent as? PsiClass)?.isAnnotationType ?: false

    if (!isAnnotationClass) {
        // check getting annotations list doesn't trigger exact resolve
        modifierList.annotations

        // check searching for non-existent annotation doesn't trigger exact resolve
        modifierList.findAnnotation("some.package.MadeUpAnnotation")
    }
}
