// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.asJava.elements.*
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.kotlinStdlib
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.kotlinStdlibCommonSources
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.kotlinStdlibJdk8
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.kotlinStdlibJdk8Sources
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.kotlinStdlibSources
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.test.assertNotNull

abstract class AbstractIdeLightClassesByFqNameTest : KotlinMultiFileLightCodeInsightFixtureTestCase() {

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        withCustomCompilerOptions(dataFile().readText(), project, module) {
            val ktFiles = files.filterIsInstance<KtFile>()
            val firstFile = ktFiles.first()
            if (firstFile.isScript()) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(firstFile)
            }

            val testData = dataFile()
            testLightClass(
                testData = testData,
                normalize = { LightClassTestCommon.removeEmptyDefaultImpls(it) },
                findLightClass = { fqName ->
                    ktFiles.firstNotNullOfOrNull { ktFile ->
                        findLightClass(fqName, ktFile, project)?.apply {
                            checkConsistency(this as KtLightClass)
                            PsiElementChecker.checkPsiElementStructure(this)
                        }
                    }
                },
            )

            for (file in ktFiles) {
                UltraLightChecker.checkClassEquivalence(file)
            }
        }
    }

    override fun getProjectDescriptor() = descriptor
}

private val descriptor = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
  listOf(kotlinStdlib, kotlinStdlibJdk8),
  listOf(kotlinStdlibSources,
         kotlinStdlibCommonSources,
         kotlinStdlibJdk8Sources)
) {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
        module.setupKotlinFacet {
            // LanguageVersionSettingsProvider#computeProjectLanguageVersionSettings uses
            // kotlin-dist-for-ide/kotlinc-dist-for-ide-from-sources/build.txt
            // as languageVersion that could point to jpsPluginVersion from model.properties
            // and could be slightly behind latest supported analysis version
            settings.languageLevel = LanguageVersion.LATEST_STABLE
        }
        super.configureModule(module, model)
    }
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
