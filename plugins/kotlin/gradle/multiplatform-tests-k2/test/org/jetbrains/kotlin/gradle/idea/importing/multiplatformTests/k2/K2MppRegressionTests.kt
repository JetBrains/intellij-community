// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests.k2

import com.intellij.ide.util.EditorHelper
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.LibraryKindsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.ReferenceTargetChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.ReferenceTargetCheckerDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.projectStructure.getKotlinLibraries
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Can be used to cover regressions that require full import or can't be checked by light tests for infrastructural reasons.
 * Please prefer the light tests for regressions whenever possible.
 *
 * @see: [org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase]
 */
@TestMetadata("multiplatform/k2/custom")
class K2MppRegressionTests : AbstractKotlinMppGradleImportingTest(), ReferenceTargetCheckerDsl {
    override val allowOnNonMac: Boolean
        get() = false

    override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K2

    /**
     * Issue: KTIJ-30257
     *
     * Check that analyzing decompiled .knm declarations with the `@ObjCMethod` annotation doesn't trigger exceptions.
     * The referenced declaration is `platform.UIKit.UIApplicationMeta#new` from the UIKit library of the K/N distribution.
     */
    @Test
    fun testObjCMethodNoExceptionsInDecompiledFiles() {
        doTest {
            onlyCheckers(HighlightingChecker, ReferenceTargetChecker)
            checkReference { referencedDeclaration ->
                val kaModule = referencedDeclaration.getKaModule(myProject, useSiteModule = null)
                assertIs<KaLibraryModule>(kaModule)
                assertTrue(
                    "A `KaLibrary` containing CInterop definitions should be a KLib-based one but was ${kaModule}",
                    kaModule.getKotlinLibraries(myProject).isNotEmpty()
                )
                val vFile = referencedDeclaration.containingKtFile.virtualFile
                val psiFile = vFile.toPsiFile(myProject)!!
                val editor = EditorHelper.openInEditor(referencedDeclaration.containingKtFile)
                CodeInsightTestFixtureImpl.instantiateAndRun(
                    psiFile, editor,
                    /* toIgnore = */ intArrayOf(),
                    /* canChangeDocument = */ false,
                )
            }
        }
    }

    @Test
    @PluginTargetVersions(gradleVersion = "7.6.3+")
    fun testComposeApp() {
        doTest {
            onlyCheckers(LibraryKindsChecker)
        }
    }

    /**
     * Relevant tickets: 
     * - KTIJ-35244 for K2 Mode
     * - KTIJ-29619 for K1 Mode
     */
    @Test
    fun testKTIJ29619KotlinJvmWithTestFixtures() {
        doTest {
            onlyCheckers(HighlightingChecker)
            hideLineMarkers = true
        }
    }
}
