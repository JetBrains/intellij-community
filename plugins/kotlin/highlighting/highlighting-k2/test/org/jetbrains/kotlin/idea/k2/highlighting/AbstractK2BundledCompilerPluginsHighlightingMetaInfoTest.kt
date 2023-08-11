// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * N.B. These test rely on the fake compiler plugins jars present in the testdata (`*_fake_plugin.jar` files).
 * Those plugins do not contain anything besides
 * 'META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar' files with corresponding
 * plugin's registrar qualified name in it, and are needed to make `KotlinK2BundledCompilerPlugins` work properly.
 */
abstract class AbstractK2BundledCompilerPluginsHighlightingMetaInfoTest : AbstractK2HighlightingMetaInfoTest() {
    override fun highlightingFileNameSuffix(testKtFile: File): String = HIGHLIGHTING_EXTENSION

    /**
     * Test cases reference fake compiler plugins' jars which lay in the test data directory. This directory is located differently
     * in local and CI (TeamCity) environments. To overcome this, we use this marker in test cases and replace it before applying
     * [withCustomCompilerOptions].
     */
    private val testDirPlaceholder: String = "\$TEST_DIR"

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val file = files.first() as KtFile

        val fileTextWithSubstitutedPath = file.text.replaceFirst(testDirPlaceholder, testDataDirectory.toString())

        withCustomCompilerOptions(fileTextWithSubstitutedPath, project, module) {
            enforceResolve(file)
            super.doMultiFileTest(files, globalDirectives)
        }
    }

    /**
     * Highlighting tests combined with compiler plugins can be flaky for some reason,
     * so we do some resolve beforehand just in case.
     */
    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun enforceResolve(file: KtFile) {
        allowAnalysisOnEdt {
            analyze(file) {
                file.declarations.forEach {
                    it.getSymbol()
                }
            }
        }
    }

    override fun getDefaultProjectDescriptor(): ProjectDescriptorWithStdlibSources {
        return object : ProjectDescriptorWithStdlibSources() {
            override fun configureModule(module: Module, model: ModifiableRootModel) {
                super.configureModule(module, model)

                // annotations for lombok plugin
                MavenDependencyUtil.addFromMaven(model, LOMBOK_MAVEN_COORDINATES)

                // annotations for serialization plugin
                MavenDependencyUtil.addFromMaven(model, KOTLINX_SERIALIZATION_CORE_MAVEN_COORDINATES)

                // annotations for parcelize plugin
                MavenDependencyUtil.addFromMaven(model, PARCELIZE_RUNTIME_MAVEN_COORDINATES)
            }
        }
    }
}

private const val LOMBOK_MAVEN_COORDINATES = "org.projectlombok:lombok:1.18.26"

private const val KOTLINX_SERIALIZATION_CORE_MAVEN_COORDINATES = "org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.0"

private const val PARCELIZE_RUNTIME_MAVEN_COORDINATES = "org.jetbrains.kotlin:kotlin-parcelize-runtime:1.8.20"
