// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.jrt.JrtFileSystem
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.incremental.storage.StringExternalizer
import org.jetbrains.kotlin.library.KLIB_FILE_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.library.KLIB_MANIFEST_FILE_NAME
import org.jetbrains.kotlin.library.KLIB_METADATA_FILE_EXTENSION
import org.jetbrains.kotlin.library.KLIB_METADATA_FOLDER_NAME
import org.jetbrains.kotlin.name.FqName

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
 * The index currently only supports JARs, KLIBs (packed and unpacked), and JRT modules. Loose `.class` files are not supported. This is
 * because it is not trivial to arrive at a binary root for a loose `.class` file (the directory structure might not correspond to the
 * package name). Such loose structures are rare, so it is currently not worth maintaining an index for it. Please use
 * [isSupportedByBinaryRootToPackageIndex] to determine if a [VirtualFile] binary root is supported by the index.
 *
 * `.kotlin_builtins` files do not need to be supported because
 * [StandardClassIds.builtInsPackages][org.jetbrains.kotlin.name.StandardClassIds.builtInsPackages] can be used to get the package names.
 */
@ApiStatus.Internal
class KotlinBinaryRootToPackageIndex : FileBasedIndexExtension<String, String>() {
    companion object {
        val NAME: ID<String, String> = ID.create(KotlinBinaryRootToPackageIndex::class.java.simpleName)
    }

    override fun getName(): ID<String, String> = NAME

    override fun dependsOnFileContent(): Boolean = true

    override fun getKeyDescriptor(): EnumeratorStringDescriptor = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): StringExternalizer = StringExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(
        JavaClassFileType.INSTANCE,
        KotlinBuiltInFileType,
        KlibMetaFileType,
    )

    override fun getVersion(): Int = 7

    override fun getIndexer(): DataIndexer<String, String, FileContent> = Indexer()

    private class Indexer : DataIndexer<String, String, FileContent> {
        override fun map(inputData: FileContent): Map<String, String> {
            try {
                val fileType = inputData.fileType
                val packageName = getPackageName(inputData, fileType) ?: return emptyMap()
                val binaryRoot = getBinaryRoot(inputData, fileType) ?: return emptyMap()

                return mapOf(binaryRoot.name to packageName.asString())
            } catch (e: Exception) {
                if (e is ControlFlowException) throw e

                throw RuntimeException("Error on indexing ${inputData.file}", e)
            }
        }

        private fun getPackageName(fileContent: FileContent, fileType: FileType): FqName? =
            when (fileType) {
                JavaClassFileType.INSTANCE -> fileContent.toKotlinJvmBinaryClass()?.packageName
                KotlinBuiltInFileType -> (readKotlinMetadataDefinition(fileContent) as? BuiltInDefinitionFile)?.packageFqName
                KlibMetaFileType -> fileContent.toCompatibleFileWithMetadata()?.packageFqName
                else -> null
            }

        private fun getBinaryRoot(inputData: FileContent, fileType: FileType): VirtualFile? =
            when (val fileSystem = inputData.file.fileSystem) {
                is JarFileSystem -> fileSystem.getLocalByEntry(inputData.file)?.takeIf { jarFile ->
                    // Check that the JAR file system is backed by either a `.jar` or `.klib` file.
                    jarFile.isPackedLibrary()
                }

                // A `.class` file in a JRT file system always has a JRT module root, which is our desired key.
                is JrtFileSystem -> inputData.file.getJrtModuleRoot()

                // We assume a regular file system here, which means the root would be a directory. Directory roots with loose files are
                // only supported for unpacked Klibs.
                else if fileType == KlibMetaFileType -> getUnpackedKlibBinaryRoot(inputData.file)

                else -> null
            }

        /**
         * The algorithm implemented here is basically a reverse of the heuristics employed in [isUnpackedKlib]. We are certain that the
         * `.knm` file is (indirectly) contained in a "linkdata" directory, and this directory is a grandchild of the unpacked Klib root.
         */
        private fun getUnpackedKlibBinaryRoot(metadataFile: VirtualFile): VirtualFile? {
            val metadataDirectory = VfsUtil.findContainingDirectory(metadataFile, KLIB_METADATA_FOLDER_NAME) ?: return null
            return metadataDirectory.parent?.parent
        }
    }
}

val VirtualFile.isSupportedByBinaryRootToPackageIndex: Boolean get() =
    isPackedLibrary() ||
            // JDK binary roots are modules in the JRT file system.
            fileSystem == JrtFileSystem.getInstance() ||
            isUnpackedKlib()

private fun VirtualFile.isPackedLibrary(): Boolean =
    name.endsWith(".jar") || name.endsWith(KLIB_FILE_EXTENSION_WITH_DOT)

/**
 * Recognizes an [unpacked Klib](https://kotlinlang.org/docs/native-libraries.html#library-format) with a heuristic. The given file must be
 * the root of the unpacked Klib, e.g. `foo/` if the packed Klib was `foo.klib`.
 *
 * Technically, the heuristic might miss unpacked Klibs without any `.knm` files, but they won't have any associated entries in the index
 * either, and it's a useless dependency, which shouldn't occur often.
 */
private fun VirtualFile.isUnpackedKlib(): Boolean {
    // At the root of an unpacked Klib is a `$component_name` directory, e.g. `default`, which itself then contains the `manifest` and
    // `.knm` files. Hence, we apply the file-specific heuristic to the directories under the root.
    return children?.any { it.isUnpackedKlibComponentDirectory() } ?: false
}

private fun VirtualFile.isUnpackedKlibComponentDirectory(): Boolean {
    if (!isDirectory) return false

    // The manifest must exist. See the Klib file structure linked above.
    val manifestFile = findChild(KLIB_MANIFEST_FILE_NAME)
    if (manifestFile == null || manifestFile.isDirectory) return false

    val metadataDirectory = findChild(KLIB_METADATA_FOLDER_NAME)
    if (metadataDirectory == null || !metadataDirectory.isDirectory) return false

    var knmFileFound = false
    VfsUtil.visitChildrenRecursively(
        metadataDirectory,
        object : VirtualFileVisitor<Unit>() {
            override fun visitFileEx(file: VirtualFile): Result =
                if (file.extension == KLIB_METADATA_FILE_EXTENSION) {
                    knmFileFound = true

                    // Stop the traversal entirely.
                    skipTo(metadataDirectory)
                } else {
                    CONTINUE
                }
        },
    )

    return knmFileFound
}
