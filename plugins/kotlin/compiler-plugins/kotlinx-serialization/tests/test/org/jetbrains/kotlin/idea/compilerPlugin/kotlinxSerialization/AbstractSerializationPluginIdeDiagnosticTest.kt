// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.checkers.AbstractKotlinHighlightVisitorTest
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinJdkAndLibraryProjectDescriptor

abstract class AbstractSerializationPluginIdeDiagnosticTest : AbstractKotlinHighlightVisitorTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinJdkAndLibraryProjectDescriptor(
            libraryFiles = listOf(
                KotlinArtifacts.instance.kotlinStdlib,
                getSerializationCoreLibraryJar()!!,
                getSerializationJsonLibraryJar()!!
            ),
            librarySourceFiles = listOf(
                KotlinArtifacts.instance.kotlinStdlibSources
            )
        )
    }
}
