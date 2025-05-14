// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.core.script.k2.modules.K2IdeScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.COMPILER_ARGUMENTS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.with

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
     * in local and CI (TeamCity) environments.
     * To overcome this, we use this path macro in test cases, and it is expected to be correctly substituted
     * by [org.jetbrains.kotlin.idea.fir.extensions.KtCompilerPluginsProviderIdeImpl].
     */
    private val testDirPlaceholder: String = "TEST_DIR"

    /**
     * We want to test the scenario for the non-yet-downloaded jars from 'kotlin-dist-for-ide' location.
     *
     * See KTIJ-32221 and [org.jetbrains.kotlin.idea.fir.extensions.FromKotlinDistForIdeByNameFallbackBundledFirCompilerPluginProvider].
     */
    private val testKotlinDistForIdePlaceholder: String = "TEST_KOTLIN_DIST_FOR_IDE"

    override fun setUp() {
        super.setUp()
        // Required to share dependencies for script modules
        project.registerExtension(
            K2IdeScriptAdditionalIdeaDependenciesProvider.EP_NAME,
            object : ScriptAdditionalIdeaDependenciesProvider {
                override fun getRelatedLibraries(
                    file: VirtualFile,
                    project: Project
                ): List<Library> {
                    return module.rootManager.orderEntries.mapNotNull { (it as? LibraryOrderEntry)?.library }.ifEmpty {
                        return emptyList()
                    }
                }
            },
            testRootDisposable
        )

        // N.B. We don't use PathMacroContributor here because it's too late to register at this point
        PathMacros.getInstance().apply {
            setMacro(testDirPlaceholder, testDataDirectory.toString())
            setMacro(testKotlinDistForIdePlaceholder, KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.toString())
        }
    }

    override fun tearDown() {
        runAll(
            {
                PathMacros.getInstance().apply {
                    setMacro(testDirPlaceholder, null)
                    setMacro(testKotlinDistForIdePlaceholder, null)
                }
            },
            { super.tearDown() },
        )
    }

    override fun getDefaultProjectDescriptor(): ProjectDescriptorWithStdlibSources =
        ProjectDescriptorWithStdlibSourcesAndExtraLibraries

    override val scriptConfigurationRefiner = TestScriptConfigurationRefiner { directives, configuration ->
        val compilerArguments = directives[COMPILER_ARGUMENTS_DIRECTIVE.dropLast(1)] ?: return@TestScriptConfigurationRefiner configuration
        module.rootManager.contentRoots

        configuration.with {
            compilerOptions.putIfAny(listOf(compilerArguments))
        }
    }
}

/**
 * A Kotlin project descriptor with STDLIB sources and extra libraries required for testing compiler plugins.
 *
 * We reuse a single instance of project descriptor so that the module and project configuration can
 * be effectively cached and reused between tests by the test infrastructure.
 */
private object ProjectDescriptorWithStdlibSourcesAndExtraLibraries : ProjectDescriptorWithStdlibSources() {

    private val extraMavenLibraries: List<String> = listOf(
        // annotations for lombok plugin
        "org.projectlombok:lombok:1.18.26",

        // annotations for serialization plugin
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.0",

        // json serialization jar to check compiled declarations with serialization annotations
        "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.5.0",

        // annotations for parcelize plugin
        "org.jetbrains.kotlin:kotlin-parcelize-runtime:1.8.20",

        // annotations for Compose compiler plugin
        "org.jetbrains.compose.runtime:runtime-desktop:1.5.0",

        // functions declarations for Kotlin DataFrame plugin
        "org.jetbrains.kotlinx:dataframe-core:0.16.0-dev-6330",
    )

    // paths are relative to `community/plugins/kotlin/idea/tests/testData/highlighterMetaInfoWithBundledCompilerPlugins`
    private val extraJarLibraries: List<String> = listOf(
        // library imitating the compose library compiled with 1.9
        "libraryWithComposeCompiledWith1.9.jar"
    )

    // points to `community/plugins/kotlin/idea/tests/testData/highlighterMetaInfoWithBundledCompilerPlugins`
    private val testDataDirectory = KotlinRoot.DIR
            .resolve("idea")
            .resolve("tests")
            .resolve("testData")
            .resolve("highlighterMetaInfoWithBundledCompilerPlugins")

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        super.configureModule(module, model)

        for (libraryCoordinates in extraMavenLibraries) {
            MavenDependencyUtil.addFromMaven(model, libraryCoordinates)
        }

        for (jarPath in extraJarLibraries) {
            val jarFile = testDataDirectory.resolve(jarPath).takeIf(File::exists) ?: error("File not found: $jarPath")
            PsiTestUtil.addLibrary(
                model,
                jarFile.name,
                jarFile.parent,
                jarFile.name
            )
        }
    }
}
