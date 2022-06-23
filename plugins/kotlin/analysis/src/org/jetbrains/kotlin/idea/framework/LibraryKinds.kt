// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.io.JarUtil
import com.intellij.openapi.vfs.*
import org.jetbrains.kotlin.caches.resolve.IdePlatformKindResolution
import org.jetbrains.kotlin.caches.resolve.resolution
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.vfilefinder.KnownLibraryKindForIndex
import org.jetbrains.kotlin.idea.vfilefinder.getLibraryKindForJar
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.utils.PathUtil
import java.util.jar.Attributes
import java.util.regex.Pattern

interface KotlinLibraryKind {
    // TODO: Drop this property. See https://youtrack.jetbrains.com/issue/KT-38233
    //  This property returns approximate library platform, as the real platform can be evaluated only for concrete library.
    val compilerPlatform: TargetPlatform
}

object JSLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("kotlin.js"), KotlinLibraryKind {
    override val compilerPlatform: TargetPlatform
        get() = JsPlatforms.defaultJsPlatform

    override fun createDefaultProperties() = DummyLibraryProperties.INSTANCE!!
}

object CommonLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("kotlin.common"), KotlinLibraryKind {
    override val compilerPlatform: TargetPlatform
        get() = CommonPlatforms.defaultCommonPlatform

    override fun createDefaultProperties() = DummyLibraryProperties.INSTANCE!!
}

// TODO: Drop this property. See https://youtrack.jetbrains.com/issue/KT-38233
//  It returns approximate library platform, as the real platform can be evaluated only for concrete library.
val PersistentLibraryKind<*>?.platform: TargetPlatform
    get() = when (this) {
        is KotlinLibraryKind -> this.compilerPlatform
        else -> JvmPlatforms.defaultJvmPlatform
    }

fun detectLibraryKind(roots: Array<VirtualFile>): PersistentLibraryKind<*>? {
    val jarFile = roots.firstOrNull() ?: return null
    if (jarFile.fileSystem is JarFileSystem) {
        // TODO: Detect library kind for Jar file using IdePlatformKindResolution.
        when (jarFile.getLibraryKindForJar()) {
            KnownLibraryKindForIndex.COMMON -> return CommonLibraryKind
            KnownLibraryKindForIndex.JS -> return JSLibraryKind
            KnownLibraryKindForIndex.UNKNOWN -> {
                /* Continue detection of library kind via IdePlatformKindResolution. */
            }
        }
    }

    val matchingResolution =
        IdePlatformKindResolution
            .getInstances()
            .firstOrNull { it.isLibraryFileForPlatform(jarFile) }

    if (matchingResolution != null) return matchingResolution.libraryKind

    return JvmPlatforms.defaultJvmPlatform.idePlatformKind.resolution.libraryKind
}

fun getLibraryJar(roots: Array<VirtualFile>, jarPattern: Pattern): VirtualFile? {
    return roots.firstOrNull { jarPattern.matcher(it.name).matches() }
}

fun getLibraryJarVersion(library: Library, jarPattern: Pattern): IdeKotlinVersion? {
    val libraryJar = getLibraryJar(library.getFiles(OrderRootType.CLASSES), jarPattern) ?: return null
    return IdeKotlinVersion.fromManifest(libraryJar)
}

fun getCommonRuntimeLibraryVersion(library: Library): IdeKotlinVersion? {
    return getLibraryJarVersion(library, PathUtil.KOTLIN_STDLIB_COMMON_JAR_PATTERN)
}
