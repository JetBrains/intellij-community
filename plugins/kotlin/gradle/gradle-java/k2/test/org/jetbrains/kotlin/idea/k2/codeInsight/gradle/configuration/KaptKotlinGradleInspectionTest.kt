// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.configuration

import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleMultiFileQuickFixTest
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.File

class KaptKotlinGradleInspectionTest : AbstractGradleMultiFileQuickFixTest() {
    override fun checkUnexpectedErrors(mainFile: File, ktFile: KtFile, fileText: String) {}

    @Test
    @TargetVersions("8.0.0+")
    fun testAddKaptCompilerPluginForAnnotationProcessorKts() {
        doMultiFileQuickFixTest(ignoreChangesInBuildScriptFiles = false)
    }
}
