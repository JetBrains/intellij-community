// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.checkers.AbstractKotlinHighlightVisitorTest
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinJdkAndLibraryProjectDescriptor

abstract class AbstractSerializationPluginIdeDiagnosticTest : AbstractKotlinHighlightVisitorTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinJdkAndLibraryProjectDescriptor(
            libraryFiles = listOf(
                TestKotlinArtifacts.kotlinStdlib,
                getSerializationCoreLibraryJar()!!,
                getSerializationJsonLibraryJar()!!
            ),
            librarySourceFiles = listOf(
                TestKotlinArtifacts.kotlinStdlibSources
            )
        )
    }
}
