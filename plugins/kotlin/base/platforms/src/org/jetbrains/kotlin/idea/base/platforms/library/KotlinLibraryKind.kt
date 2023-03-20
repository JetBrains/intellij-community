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

// TODO: Drop this property. See https://youtrack.jetbrains.com/issue/KT-38233
//  It returns approximate library platform, as the real platform can be evaluated only for concrete library.
val PersistentLibraryKind<*>?.platform: TargetPlatform
    get() = when (this) {
        is KotlinLibraryKind -> this.compilerPlatform
        else -> JvmPlatforms.defaultJvmPlatform
    }

fun detectLibraryKind(library: Library, project: Project): PersistentLibraryKind<*>? =
    project.service<LibraryEffectiveKindProvider>().getEffectiveKind(library)
