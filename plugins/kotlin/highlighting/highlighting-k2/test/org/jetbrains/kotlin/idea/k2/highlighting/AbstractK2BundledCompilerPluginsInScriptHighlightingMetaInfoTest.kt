// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.registerExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.idea.core.script.v1.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.COMPILER_ARGUMENTS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.with

/**
 * @see [AbstractK2ScriptHighlightingMetaInfoTest] for base setup
 */
abstract class AbstractK2BundledCompilerPluginsInScriptHighlightingMetaInfoTest : AbstractK2ScriptHighlightingMetaInfoTest() {
    override fun highlightingFileNameSuffix(testKtFile: File): String = HIGHLIGHTING_EXTENSION

    override fun getDefaultProjectDescriptor(): ProjectDescriptorWithStdlibSources =
        ProjectDescriptorWithStdlibSourcesAndExtraLibraries

    override fun setUp() {
        super.setUp()
        // Required to share dependencies for script modules
        project.registerExtension(
            ScriptAdditionalIdeaDependenciesProvider.EP_NAME,
            object : ScriptAdditionalIdeaDependenciesProvider {
                override fun getRelatedLibraries(
                    file: VirtualFile,
                    project: Project
                ): List<Library> {
                    return module.rootManager.orderEntries.mapNotNull {
                        (it as? LibraryOrderEntry)?.library
                    }
                }
            },
            testRootDisposable
        )

        // N.B. We don't use PathMacroContributor here because it's too late to register at this point
        K2TestMetaDataWithBundledPluginsDefaultDirsMacrosHelper.setUpMacros(testDataDirectory)
    }

    override fun doMultiFileTest(
        files: List<PsiFile>,
        globalDirectives: Directives
    ) {
        runWriteAction {
            DefaultScriptResolutionStrategy.getInstance(project).execute(*(files.mapNotNull { it as? KtFile }.toTypedArray()))
        }
    }

    override fun tearDown() {
        runAll(
            {
                K2TestMetaDataWithBundledPluginsDefaultDirsMacrosHelper.tearDownMacros()
            },
            { super.tearDown() },
        )
    }

    override fun refineScriptCompilationConfiguration(
        globalDirectives: Directives,
        configuration: ScriptCompilationConfiguration
    ): ScriptCompilationConfiguration {
        val compilerArguments = globalDirectives.listValues(COMPILER_ARGUMENTS_DIRECTIVE.removeSuffix(":"))
        if (compilerArguments == null) {
            return configuration
        }

        return configuration.with {
            compilerOptions.putIfAny(compilerArguments)
        }
    }
}