// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmJsLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmWasiLibraryKind
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.platform.impl.WasmJsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.WasmWasiIdePlatformKind
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

abstract class AbstractWasmIdePlatformKindTooling : IdePlatformKindTooling() {

    override val mavenLibraryIds = emptyList<String>()
    override val gradlePluginId = ""
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.WASM)

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        return false
    }

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        return null
    }
}

abstract class AbstractWasmJsIdePlatformKindTooling : AbstractWasmIdePlatformKindTooling() {

    override val kind = WasmJsIdePlatformKind

    override val libraryKind = KotlinWasmJsLibraryKind
}

abstract class AbstractWasmWasiIdePlatformKindTooling : AbstractWasmIdePlatformKindTooling() {

    override val kind = WasmWasiIdePlatformKind

    override val libraryKind = KotlinWasmWasiLibraryKind
}