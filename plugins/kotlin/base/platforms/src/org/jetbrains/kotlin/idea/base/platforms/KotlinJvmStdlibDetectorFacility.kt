// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.utils.PathUtil.KOTLIN_RUNTIME_JAR_PATTERN

object KotlinJvmStdlibDetectorFacility : StdlibDetectorFacility() {
    override val supportedLibraryKind: KotlinLibraryKind?
        get() = null

    override fun getStdlibJar(roots: List<VirtualFile>): VirtualFile? {
        for (root in roots) {
            if (KOTLIN_RUNTIME_JAR_PATTERN.matcher(root.name).matches()) {
                return root
            }
        }
        return null
    }
}