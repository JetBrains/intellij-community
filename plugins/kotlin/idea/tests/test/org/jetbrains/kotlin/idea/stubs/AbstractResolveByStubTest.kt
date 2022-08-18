// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubs

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.util.DescriptorValidator.ValidationVisitor.errorTypesForbidden
import org.jetbrains.kotlin.idea.test.util.RecursiveDescriptorComparator
import org.jetbrains.kotlin.load.java.descriptors.PossiblyExternalAnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import java.io.File

abstract class AbstractResolveByStubTest : KotlinLightCodeInsightFixtureTestCase() {
    protected fun doTest(testFileName: String) {
        if (InTextDirectivesUtils.isDirectiveDefined(dataFile().readText(), "NO_CHECK_SOURCE_VS_BINARY")) {
            // If NO_CHECK_SOURCE_VS_BINARY is enabled, source vs binary descriptors differ, which means that we should not run this test:
            // it would compare descriptors resolved from sources (by stubs) with .txt, which describes binary descriptors
            return
        }

        val fileName = fileName()
        myFixture.configureByFile(fileName)
        val shouldFail = getTestName(false) == "ClassWithConstVal"
        AstAccessControl.testWithControlledAccessToAst(shouldFail, project, testRootDisposable) {
            performTest(dataFilePath(fileName()))
        }
    }

    // In compiler repo for these test MOCK_JDK is used which is currently 1.6 JDK
    override fun getProjectDescriptor(): LightProjectDescriptor = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
        listOf(TestKotlinArtifacts.kotlinStdlib), listOf(TestKotlinArtifacts.kotlinStdlibSources)
    ) {
        override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk16()
    }

    private fun performTest(path: String) {
        val file = file as KtFile

        withCustomCompilerOptions(file.text, project, module) {
            val module = file.findModuleDescriptor()
            val packageViewDescriptor = module.getPackage(FqName("test"))
            Assert.assertFalse(packageViewDescriptor.isEmpty())

            val fileToCompareTo = File(FileUtil.getNameWithoutExtension(path) + ".txt")

            RecursiveDescriptorComparator.validateAndCompareDescriptorWithFile(
                packageViewDescriptor,
                RecursiveDescriptorComparator.DONT_INCLUDE_METHODS_OF_OBJECT
                    .filterRecursion(RecursiveDescriptorComparator.SKIP_BUILT_INS_PACKAGES)
                    .checkPrimaryConstructors(true)
                    .checkPropertyAccessors(true)
                    .withValidationStrategy(errorTypesForbidden())
                    .withRendererOptions { options ->
                        options.annotationFilter = { annotationDescriptor ->
                            annotationDescriptor !is PossiblyExternalAnnotationDescriptor || !annotationDescriptor.isIdeExternalAnnotation
                        }
                    },
                fileToCompareTo,
            )
        }
    }
}
