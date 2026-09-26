// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.utils.PathUtil
import java.util.regex.Pattern

@ApiStatus.Internal
object KotlinJavaScriptStdlibDetectorFacility : StdlibDetectorFacility() {
    private val KOTLIN_JS_LIBRARY_KLIB_PATTERN = Pattern.compile("kotlin-stdlib-js.*\\.klib")

    override val supportedLibraryKind: KotlinLibraryKind
        get() = KotlinJavaScriptLibraryKind

    override fun getStdlibJar(roots: List<VirtualFile>): VirtualFile? {
        for (root in roots) {
            // KLIBs fall under the JAR file system as well
            if (root.fileSystem.protocol !== StandardFileSystems.JAR_PROTOCOL) continue

            val name = root.url.substringBefore("!/").substringAfterLast('/')
            if (name == KotlinArtifactNames.KOTLIN_STDLIB_JS
                || name == "kotlin-jslib.jar" // Outdated JS stdlib name
                || PathUtil.KOTLIN_STDLIB_JS_JAR_PATTERN.matcher(name).matches()
                || PathUtil.KOTLIN_JS_LIBRARY_JAR_PATTERN.matcher(name).matches()
                || KOTLIN_JS_LIBRARY_KLIB_PATTERN.matcher(name).matches()
            ) {
                val jar = VfsUtilCore.getVirtualFileForJar(root) ?: continue
                return jar
            }
        }

        return null
    }
}
