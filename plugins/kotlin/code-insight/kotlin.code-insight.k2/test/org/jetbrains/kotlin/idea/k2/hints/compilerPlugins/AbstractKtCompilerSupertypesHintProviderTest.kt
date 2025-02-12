// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints.compilerPlugins

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.kotlin.idea.codeInsight.hints.AbstractKotlinInlayHintsProviderTest
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.KtCompilerSupertypesHintProvider
import org.jetbrains.kotlin.idea.resolve.KOTLINX_SERIALIZATION_CORE_JVM_MAVEN_COORDINATES
import org.jetbrains.kotlin.idea.resolve.loadSingleJarFromMaven
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot
import java.io.File

abstract class AbstractKtCompilerSupertypesHintProviderTest : AbstractKotlinInlayHintsProviderTest() {
    override fun inlayHintsProvider(): InlayHintsProvider =
        KtCompilerSupertypesHintProvider()

    override fun doTest(testPath: String) {
        module.withCompilerPlugin(
            KotlinK2BundledCompilerPlugins.KOTLINX_SERIALIZATION_COMPILER_PLUGIN,
        ) {
            super.doTest(testPath)
        }
    }
}