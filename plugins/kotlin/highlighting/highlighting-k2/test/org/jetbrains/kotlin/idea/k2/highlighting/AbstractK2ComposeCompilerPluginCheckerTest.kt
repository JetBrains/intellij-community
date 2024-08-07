// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import java.io.File

abstract class AbstractK2ComposeCompilerPluginCheckerTest : AbstractK2BundledCompilerPluginsHighlightingMetaInfoTest() {
    override fun highlightingFileNameSuffix(testKtFile: File): String = HIGHLIGHTING_EXTENSION

    override fun getDefaultProjectDescriptor(): ProjectDescriptorWithStdlibSources =
        ProjectDescriptorWithStdlibSourcesAndComposeRuntime
}

/**
 * A Kotlin project descriptor with STDLIB sources and compose runtime libraries required for testing the compose compiler plugin.
 *
 * We reuse a single instance of project descriptor so that the module and project configuration can
 * be effectively cached and reused between tests by the test infrastructure.
 */
private object ProjectDescriptorWithStdlibSourcesAndComposeRuntime : ProjectDescriptorWithStdlibSources() {

    private val composeRuntimeMavenLibraries: List<String> = listOf(
        // annotations for Compose compiler plugin
        "org.jetbrains.compose.runtime:runtime-desktop:1.5.0",
    )

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        super.configureModule(module, model)

        for (library in composeRuntimeMavenLibraries) {
            MavenDependencyUtil.addFromMaven(model, library)
        }
    }
}