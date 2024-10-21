// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IdeaModuleInfoUtils")
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.projectStructure.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.isWasm
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.platform.wasm.isWasmJs
import org.jetbrains.kotlin.platform.wasm.isWasmWasi

@K1ModeProjectStructureApi
val BinaryModuleInfo.binariesScope: GlobalSearchScope
    get() {
        val contentScope = contentScope
        if (GlobalSearchScope.isEmptyScope(contentScope)) {
            return contentScope
        }

        val project = contentScope.project
            ?: error("Project is empty for scope $contentScope (${contentScope.javaClass.name})")

        return KotlinSourceFilterScope.libraryClasses(contentScope, project)
    }

@OptIn(K1ModeProjectStructureApi::class)
internal val LOG = Logger.getInstance(IdeaModuleInfo::class.java)

@K1ModeProjectStructureApi
internal fun TargetPlatform.canDependOn(other: IdeaModuleInfo, isHmppEnabled: Boolean): Boolean {
    if (isHmppEnabled) {
        // HACK: allow depending on stdlib even if platforms do not match
        if (isNative() && other is AbstractKlibLibraryInfo && other.libraryRoot.endsWith(KONAN_STDLIB_NAME)) return true

        val platformsWhichAreNotContainedInOther = this.componentPlatforms - other.platform.componentPlatforms
        if (platformsWhichAreNotContainedInOther.isEmpty()) return true

        // unspecifiedNativePlatform is effectively a wildcard for NativePlatform
        return platformsWhichAreNotContainedInOther.all { it is NativePlatform } &&
                NativePlatforms.unspecifiedNativePlatform.componentPlatforms.single() in other.platform.componentPlatforms
    } else {
        return this.isJvm() && other.platform.isJvm() ||
                this.isJs() && other.platform.isJs() ||
                this.isWasmJs() && other.platform.isWasmJs() ||
                this.isWasmWasi() && other.platform.isWasmWasi() ||
                this.isWasm() && other.platform.isWasm() ||
                this.isNative() && other.platform.isNative() ||
                this.isCommon() && other.platform.isCommon()
    }
}

@K1ModeProjectStructureApi
fun IdeaModuleInfo.isLibraryClasses() = this is SdkInfo || this is LibraryInfo

@K1ModeProjectStructureApi
fun IdeaModuleInfo.projectSourceModules(): List<ModuleSourceInfo> {
    return when (this) {
        is ModuleSourceInfo -> listOf(this)
        is PlatformModuleInfo -> containedModules
        else -> emptyList()
    }
}

@get:ApiStatus.Internal
@Deprecated("Use org.jetbrains.kotlin.idea.base.projectStructure.kotlinSourceRootType' instead.")
@K1ModeProjectStructureApi
val ModuleSourceInfo.sourceType: SourceType
    get() = when (kotlinSourceRootType) {
        SourceKotlinRootType -> SourceType.PRODUCTION
        TestSourceKotlinRootType -> SourceType.TEST
        null -> SourceType.PRODUCTION
    }
