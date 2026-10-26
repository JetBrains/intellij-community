// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.testFramework.TestKotlinArtifactsProvider
import java.nio.file.Path
import kotlin.io.path.exists

internal class TestKotlinArtifactsProviderImpl: TestKotlinArtifactsProvider {
    override fun getJpsPluginClasspath(): List<Path> = TestKotlinArtifacts.jpsPluginClasspath
    override fun getKotlincCompilerCli(): Path = TestKotlinArtifacts.kotlinDistForIdeUnpacked
    override fun getIdeKotlinMainKts(): Path =
        TestKotlinArtifacts.kotlinDistForIdeAnalyzerUnpacked.resolve("lib/${KotlinArtifactNames.KOTLIN_MAIN_KTS}")
            .also { check(it.exists()) { "$it doesn't exist" } }
}
