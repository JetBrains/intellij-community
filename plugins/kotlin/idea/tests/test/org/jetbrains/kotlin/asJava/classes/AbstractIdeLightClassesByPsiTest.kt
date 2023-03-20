// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.asJava.classes.UltraLightChecker.checkByJavaFile
import org.jetbrains.kotlin.asJava.classes.UltraLightChecker.checkDescriptorsLeak
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinMultiFileLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractIdeLightClassesByPsiTest : KotlinMultiFileLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val sourceText = dataFile().readText()
        withCustomCompilerOptions(sourceText, project, module) {
            val ktFiles = files.filterIsInstance<KtFile>()
            val ktFile = ktFiles.first()
            if (ktFile.isScript()) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(ktFile)
            }

            val classes = ktFiles.flatMap {
                listOfNotNull(it.script?.toLightClass(), it.findFacadeClass()) +
                        UltraLightChecker.allClasses(it).mapNotNull(KtClassOrObject::toLightClass)
            }

            checkByJavaFile(dataFile().path, classes)
            classes.forEach { checkDescriptorsLeak(it) }
            for (ktClass in classes.mapNotNull { it.kotlinOrigin }) {
                val ultraLightClass = UltraLightChecker.checkClassEquivalence(ktClass)
                if (ultraLightClass != null) {
                    checkDescriptorsLeak(ultraLightClass)
                }
            }
        }
    }
}
