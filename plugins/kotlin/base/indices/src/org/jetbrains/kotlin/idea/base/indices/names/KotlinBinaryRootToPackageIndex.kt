// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.jrt.JrtFileSystem
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.incremental.storage.StringExternalizer
import org.jetbrains.kotlin.library.KLIB_FILE_EXTENSION_WITH_DOT

/**
 * [KotlinBinaryRootToPackageIndex] maps JAR and KLIB library names and JRT module names to the packages that are contained in the library.
 *
 * The index key is the *simple* file name of the library, for example `fooBar.jar` for a JAR file `project-root/libs/essential/fooBar.jar`.
 * The index is thus not guaranteed to provide package names for only a specific library file, in case multiple libraries share the same
 * file name. This is acceptable because the index is used by K2 symbol providers, which allow false positives in package sets.
 *
 * If the index contains no values for some specific key, it means that the library contains no packages which contain compiled Kotlin code.
 * This also applies to JDK modules.
 *
 * The index currently only supports JARs, KLIBs, and JRT modules. Loose `.class` files and unpacked KLIBs are not supported. This is
 * because it is not trivial to arrive at a binary root for a loose `.class` file (the directory structure might not correspond to the
 * package name). Such loose structures are rare, so it is currently not worth maintaining an index for it. Please use
 * [isSupportedByBinaryRootToPackageIndex] to determine if a [VirtualFile] binary root is supported by the index.
 *
 * `.kotlin_builtins` files do not need to be supported because
 * [StandardClassIds.builtInsPackages][org.jetbrains.kotlin.name.StandardClassIds.builtInsPackages] can be used to get the package names.
 */
class KotlinBinaryRootToPackageIndex : FileBasedIndexExtension<String, String>() {
    companion object {
        val NAME: ID<String, String> = ID.create(KotlinBinaryRootToPackageIndex::class.java.simpleName)
    }

    override fun getName() = NAME

    override fun dependsOnFileContent() = true

    override fun getKeyDescriptor() = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer() = StringExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(
        JavaClassFileType.INSTANCE,
        KlibMetaFileType,
    )

    override fun getVersion(): Int = 4

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { fileContent ->
        try {
            val packageName = when (fileContent.fileType) {
                JavaClassFileType.INSTANCE -> fileContent.toKotlinJvmBinaryClass()?.packageName
                KlibMetaFileType -> fileContent.toCompatibleFileWithMetadata()?.packageFqName
                else -> null
            }

            if (packageName == null) {
                return@DataIndexer emptyMap()
            }

            val binaryRoot = when (val fileSystem = fileContent.file.fileSystem) {
                is JarFileSystem -> fileSystem.getLocalByEntry(fileContent.file)?.takeIf { jarFile ->
                    // Check that the JAR file system is backed by either a `.jar` or `.klib` file.
                    jarFile.isSupportedByBinaryRootToPackageIndex
                }

                // A `.class` file in a JRT file system always has a JRT module root, which is our desired key.
                is JrtFileSystem -> fileContent.file.getJrtModuleRoot()

                else -> null
            }

            if (binaryRoot == null) {
                return@DataIndexer emptyMap()
            }

            return@DataIndexer mapOf(binaryRoot.name to packageName.asString())
        } catch (e: Exception) {
            if (e is ControlFlowException) throw e

            throw RuntimeException("Error on indexing ${fileContent.file}", e)
        }
    }
}

val VirtualFile.isSupportedByBinaryRootToPackageIndex: Boolean get() =
    name.endsWith(".jar") ||
            name.endsWith(KLIB_FILE_EXTENSION_WITH_DOT) ||
            // JDK binary roots are modules in the JRT file system.
            fileSystem == JrtFileSystem.getInstance()
