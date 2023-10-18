// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.incremental.storage.StringExternalizer
import kotlin.jvm.java

/**
 * [KotlinBinaryRootToPackageIndex] maps JAR and KLIB library names to the packages that are contained in the library.
 *
 * The index key is the *simple* file name of the library, for example `fooBar.jar` for a JAR file `project-root/libs/essential/fooBar.jar`.
 * The index is thus not guaranteed to provide package names for only a specific library file, in case multiple libraries share the same
 * file name. This is acceptable because the index is used by K2 symbol providers, which allow false positives in package sets.
 *
 * If the index contains no values for some specific key, it means that the library contains no packages which contain compiled Kotlin code.
 *
 * `.kotlin_builtins` files do not need to be supported because
 * [StandardClassIds.builtInsPackages][org.jetbrains.kotlin.name.StandardClassIds.builtInsPackages] can be used to get the package names.
 */
class KotlinBinaryRootToPackageIndex : FileBasedIndexExtension<String, String>() {
    companion object {
        val NAME: ID<String, String> = ID.create(KotlinBinaryRootToPackageIndex::class.java.canonicalName)
    }

    override fun getName() = NAME

    override fun dependsOnFileContent() = true

    override fun getKeyDescriptor() = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer() = StringExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(
        JavaClassFileType.INSTANCE,
        KlibMetaFileType,
    )

    override fun getVersion(): Int = 2

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

            // The file is in a JAR filesystem (even for KLIBs), whose root is the JAR/KLIB file itself.
            val binaryRoot = JarFileSystem.getInstance().getVirtualFileForJar(fileContent.file) ?: return@DataIndexer emptyMap()

            return@DataIndexer mapOf(binaryRoot.name to packageName.asString())
        } catch (e: Exception) {
            if (e is ControlFlowException) throw e

            throw RuntimeException("Error on indexing ${fileContent.file}", e)
        }
    }
}
