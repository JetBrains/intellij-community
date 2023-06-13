// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms

sealed interface KotlinLibraryKind {
    // TODO: Drop this property. See https://youtrack.jetbrains.com/issue/KT-38233
    //  This property returns approximate library platform, as the real platform can be evaluated only for concrete library.
    val compilerPlatform: TargetPlatform
}

object KotlinJavaScriptLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("kotlin.js"), KotlinLibraryKind {
    override val compilerPlatform: TargetPlatform
        get() = JsPlatforms.defaultJsPlatform

    override fun createDefaultProperties(): DummyLibraryProperties {
        return DummyLibraryProperties.INSTANCE
    }
}

object KotlinWasmLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("kotlin.wasm"), KotlinLibraryKind {
    override val compilerPlatform: TargetPlatform
        get() = WasmPlatforms.Default

    override fun createDefaultProperties(): DummyLibraryProperties {
        return DummyLibraryProperties.INSTANCE
    }
}

object KotlinCommonLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("kotlin.common"), KotlinLibraryKind {
    override val compilerPlatform: TargetPlatform
        get() = CommonPlatforms.defaultCommonPlatform

    override fun createDefaultProperties(): DummyLibraryProperties {
        return DummyLibraryProperties.INSTANCE
    }
}

object KotlinNativeLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("kotlin.native"), KotlinLibraryKind {
    override val compilerPlatform: TargetPlatform
        get() = NativePlatforms.unspecifiedNativePlatform

    override fun createDefaultProperties(): DummyLibraryProperties {
        return DummyLibraryProperties.INSTANCE
    }
}

/**
 * This kind is not expected to be assigned to workspace libraries.
 * Its only purpose it to prevent repeated evaluation of (absent) PersistentLibraryKind for Kotlin JVM libraries.
 * com.intellij.externalSystem.ImportedLibraryType's persistent kind is expected to be preserved for them when exists.
 */
internal object KotlinJvmEffectiveLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("kotlin.jvm"), KotlinLibraryKind {
    override val compilerPlatform: TargetPlatform
        get() = JvmPlatforms.defaultJvmPlatform

    override fun createDefaultProperties(): DummyLibraryProperties {
        return DummyLibraryProperties.INSTANCE
    }
}


// TODO: Drop this property. See https://youtrack.jetbrains.com/issue/KT-38233
//  It returns approximate library platform, as the real platform can be evaluated only for concrete library.
val PersistentLibraryKind<*>?.platform: TargetPlatform
    get() = when (this) {
        is KotlinLibraryKind -> this.compilerPlatform
        else -> JvmPlatforms.defaultJvmPlatform
    }

fun detectLibraryKind(library: Library, project: Project): PersistentLibraryKind<*>? =
    project.service<LibraryEffectiveKindProvider>().getEffectiveKind(library)
