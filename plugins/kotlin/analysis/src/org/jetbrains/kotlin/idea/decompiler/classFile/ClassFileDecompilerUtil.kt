// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.classFile

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ClassFileViewProvider
import org.jetbrains.kotlin.idea.caches.FileAttributeService
import org.jetbrains.kotlin.idea.caches.IDEKotlinBinaryClassCache
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.load.kotlin.findKotlinClass
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

data class IsKotlinBinary(val isKotlinBinary: Boolean, val timestamp: Long)

val KOTLIN_COMPILED_FILE_ATTRIBUTE: String = "kotlin-compiled-file".apply {
    serviceOrNull<FileAttributeService>()?.register(this, 1)
}

val KEY = Key.create<IsKotlinBinary>(KOTLIN_COMPILED_FILE_ATTRIBUTE)

/**
 * Checks if this file is a compiled Kotlin class file ABI-compatible with the current plugin
 */
fun isKotlinWithCompatibleAbiVersion(file: VirtualFile): Boolean {
    val ideKotlinBinaryClassCache = IDEKotlinBinaryClassCache.getInstance()
    if (!ideKotlinBinaryClassCache.isKotlinJvmCompiledFile(file)) return false

    val kotlinClass = ideKotlinBinaryClassCache.getKotlinBinaryClassHeaderData(file)
    return kotlinClass != null && kotlinClass.metadataVersion.isCompatible()
}

/**
 * Checks if this file is a compiled "internal" Kotlin class, i.e. a Kotlin class (not necessarily ABI-compatible with the current plugin)
 * which should NOT be decompiled (and, as a result, shown under the library in the Project view, be searchable via Find class, etc.)
 */
fun isKotlinInternalCompiledFile(file: VirtualFile, fileContent: ByteArray? = null): Boolean {
    // Don't crash on invalid files (EA-97751)
    if (!file.isValid || fileContent?.size == 0 || !file.exists()) {
        return false
    }

    val ideKotlinBinaryClassCache = IDEKotlinBinaryClassCache.getInstance()

    if (!ideKotlinBinaryClassCache.isKotlinJvmCompiledFile(file, fileContent)) {
        return false
    }

    val innerClass =
        try {
            if (fileContent == null) {
                ClassFileViewProvider.isInnerClass(file)
            } else {
                ClassFileViewProvider.isInnerClass(file, fileContent)
            }
        } catch (exception: Exception) {
            Logger
                .getInstance("org.jetbrains.kotlin.idea.decompiler.classFile.isKotlinInternalCompiledFile")
                .debug(file.path, exception)

            return false
        }

    if (innerClass) {
        return true
    }

    val header = ideKotlinBinaryClassCache.getKotlinBinaryClassHeaderData(file, fileContent) ?: return false
    if (header.classId.isLocal) return true

    return header.kind == KotlinClassHeader.Kind.SYNTHETIC_CLASS ||
            header.kind == KotlinClassHeader.Kind.MULTIFILE_CLASS_PART
}

fun findMultifileClassParts(file: VirtualFile, classId: ClassId, partNames: List<String>): List<KotlinJvmBinaryClass> {
    val packageFqName = classId.packageFqName
    val partsFinder = DirectoryBasedClassFinder(file.parent!!, packageFqName)

    return partNames.mapNotNull {
        partsFinder.findKotlinClass(ClassId(packageFqName, Name.identifier(it.substringAfterLast('/'))))
    }
}
