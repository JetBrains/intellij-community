// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmJsLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmWasiLibraryKind
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.platform.impl.WasmJsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.WasmWasiIdePlatformKind
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

interface KotlinWasmRunConfigurationDataProvider

abstract class AbstractWasmIdePlatformKindTooling : IdePlatformKindTooling() {

    override val mavenLibraryIds: List<String> get() = emptyList<String>()
    override val gradlePluginId: String get() = ""
    override val gradlePlatformIds: List<KotlinPlatform> = listOf(KotlinPlatform.WASM)

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean = false

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        if (!allowSlowOperations) {
            return null
        }

        val initialLocations = computeInitialIconLocations(declaration) ?: return null
        return testIconProvider.getGenericTestIcon(declaration, initialLocations)
    }

    private fun computeInitialIconLocations(declaration: KtNamedDeclaration): List<String>? {
        val hasWasmConfigurationProviders = RunConfigurationProducer
            .getProducers(declaration.project)
            .asSequence()
            .filterIsInstance<KotlinWasmRunConfigurationDataProvider>()
            .any()

        if (!hasWasmConfigurationProviders) return null

        val location = declaration.containingKtFile.packageFqName.asString()

        return SmartList(location)
    }
}

abstract class AbstractWasmJsIdePlatformKindTooling : AbstractWasmIdePlatformKindTooling() {

    override val kind: WasmJsIdePlatformKind get() = WasmJsIdePlatformKind

    override val libraryKind: KotlinWasmJsLibraryKind get() = KotlinWasmJsLibraryKind
}

abstract class AbstractWasmWasiIdePlatformKindTooling : AbstractWasmIdePlatformKindTooling() {

    override val kind: WasmWasiIdePlatformKind get() = WasmWasiIdePlatformKind

    override val libraryKind: KotlinWasmWasiLibraryKind get() = KotlinWasmWasiLibraryKind
}