// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInsVirtualFileProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInDecompilationInterceptor
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinMetadataStubBuilder

/**
 * Decompiles .kotlin_builtins files that belong to the kotlin-stdlib from the plugin classpath without class filtering.
 * The decompiled classes from these files are used in the symbol provider for built-ins in all modules, including non-JVM.
 * For common modules in particular, the lack of these classes leads to unresolved code, as the declarations are not published
 * in .kotlin_medata files of kotlin-stdlib-common.
 * See [org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile].
 */
internal class K2KotlinBuiltInDecompilationInterceptor : KotlinBuiltInDecompilationInterceptor {
    override fun readFile(bytes: ByteArray, file: VirtualFile): KotlinMetadataStubBuilder.FileWithMetadata? {
        if (file in BuiltInsVirtualFileProvider.getInstance().getBuiltInVirtualFiles())
            return BuiltInDefinitionFile.read(bytes, file, filterOutClassesExistingAsClassFiles = false)
        else return null
    }
}
