// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.utils.LibraryUtils
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

@ApiStatus.Internal
object JsStdlibDetectionUtil {
    private val IS_JS_LIBRARY_STD_LIB = Key.create<Boolean>("IS_JS_LIBRARY_STD_LIB")

    fun hasJavaScriptStdlibJar(library: Library, project: Project, ignoreKind: Boolean = false): Boolean {
        if (library !is LibraryEx || library.isDisposed) return false
        if (!ignoreKind && !isJavaScriptLibrary(library, project)) return false

        val classes = listOf(*library.getFiles(OrderRootType.CLASSES))
        return getJavaScriptStdLibJar(classes) != null
    }

    fun getJavaScriptLibraryStdVersion(library: Library, project: Project): IdeKotlinVersion? {
        if (!isJavaScriptLibrary(library, project)) return null
        val jar = getJavaScriptStdLibJar(library.getFiles(OrderRootType.CLASSES).toList()) ?: return null
        return IdeKotlinVersion.fromManifest(jar)
    }

    private fun isJavaScriptLibrary(library: Library, project: Project): Boolean {
        return LibraryEffectiveKindProvider.getInstance(project).getEffectiveKind(library as LibraryEx) is KotlinJavaScriptLibraryKind
    }

    fun getJavaScriptStdLibJar(classesRoots: List<VirtualFile>): VirtualFile? {
        for (root in classesRoots) {
            if (root.fileSystem.protocol !== StandardFileSystems.JAR_PROTOCOL) continue

            val name = root.url.substringBefore("!/").substringAfterLast('/')
            if (name == KotlinArtifactNames.KOTLIN_STDLIB_JS
                || name == "kotlin-jslib.jar" // Outdated JS stdlib name
                || PathUtil.KOTLIN_STDLIB_JS_JAR_PATTERN.matcher(name).matches()
                || PathUtil.KOTLIN_JS_LIBRARY_JAR_PATTERN.matcher(name).matches()
            ) {

                val jar = VfsUtilCore.getVirtualFileForJar(root) ?: continue
                var isJSStdLib = jar.getUserData(IS_JS_LIBRARY_STD_LIB)
                if (isJSStdLib == null) {
                    isJSStdLib = LibraryUtils.isKotlinJavascriptStdLibrary(File(jar.path))
                    jar.putUserData(IS_JS_LIBRARY_STD_LIB, isJSStdLib)
                }

                if (isJSStdLib) {
                    return jar
                }
            }
        }

        return null
    }
}
