// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmLibraryKind
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.run.multiplatform.KotlinMultiplatformRunLocationsProvider
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.WasmIdePlatformKind
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.utils.PathUtil
import org.jetbrains.kotlin.utils.ifEmpty
import javax.swing.Icon

abstract class AbstractWasmIdePlatformKindTooling : IdePlatformKindTooling() {

    override val kind = WasmIdePlatformKind

    override val mavenLibraryIds = emptyList<String>()
    override val gradlePluginId = ""
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.WASM)

    override val libraryKind = KotlinWasmLibraryKind

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        return false
    }

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        return null
    }
}