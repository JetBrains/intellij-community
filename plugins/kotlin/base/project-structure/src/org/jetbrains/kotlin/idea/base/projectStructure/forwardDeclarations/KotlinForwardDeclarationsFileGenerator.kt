// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.NativeForwardDeclarationKind
import org.jetbrains.kotlin.name.NativeStandardInteropNames.ExperimentalForeignApi
import org.jetbrains.kotlin.name.NativeStandardInteropNames.cInteropPackage
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private val LOG = logger<KotlinForwardDeclarationsFileGenerator>()

/**
 * Generator of synthetic K/N forward declaration files.
 *
 * These files provide the necessary source for otherwise bodiless declarations, thus enabling the usual IDE functionality.
 * The files are not a compilation input, but a mere visualization of K/N forward declaration symbols.
 * It is important to note that since the generated declarations are not the real origin of the symbols,
 * there is currently no mechanism for ensuring the text version represents all symbols details correctly.
 *
 * The generated declarations are grouped into files by package.
 * The exact form of the declaration depends on the package, see [org.jetbrains.kotlin.name.NativeForwardDeclarationKind].
 *
 * See also:
 * [KotlinForwardDeclarationsWorkspaceEntity] workspace model entity for storing the information about the extra roots.
 * [KotlinForwardDeclarationsModelChangeService] service responsible for launching the generation.
 */
internal object KotlinForwardDeclarationsFileGenerator {
    suspend fun generateForwardDeclarationFiles(library: KLibRoot): Path? {
        val groupedClasses = KotlinForwardDeclarationsFqNameExtractor.getGroupedForwardDeclarations(library).ifEmpty { return null }
        return generateForwardDeclarationsForFqNames(groupedClasses, library.libraryRoot)
    }

    private suspend fun generateForwardDeclarationsForFqNames(
        groupedFqNames: Map<FqName, List<FqName>>,
        libraryPath: String,
    ): Path {
        val root = KotlinForwardDeclarationsFileSystem.storageRootPath
        val libraryLocation = root.resolve(libraryPath.removePrefix("/"))

        val filesToGenerate = groupedFqNames.mapNotNull { (pkg, classes) ->
            val kind = NativeForwardDeclarationKind.packageFqNameToKind[pkg] ?: run {
                LOG.warn("Skipping request to generate K/N forward declarations with an unsupported package $pkg")
                return@mapNotNull null
            }
            "${pkg.asString()}.kt" to createText(pkg, kind, classes)
        }
        if (filesToGenerate.isEmpty()) return libraryLocation

        edtWriteAction {
            val baseDir = VfsUtil.createDirectoryIfMissing(libraryLocation.absolutePathString()) ?: return@edtWriteAction
            for ((fileName, text) in filesToGenerate) {
                val file = baseDir.findChild(fileName) ?: baseDir.createChildData(this, fileName)
                VfsUtil.saveText(file, text)
            }
        }
        return libraryLocation
    }

    private fun createText(pkg: FqName, kind: NativeForwardDeclarationKind, classes: List<FqName>): String {
        val classKindCodeRepresentation = kind.classKind.codeRepresentation ?: "".also {
            LOG.error("Error generating forward declarations with kind $kind for package $pkg")
        }
        val constructor = when (kind) {
            NativeForwardDeclarationKind.Struct -> "private constructor(rawPtr: $cInteropPackage.NativePtr) "
            NativeForwardDeclarationKind.ObjCClass,
            NativeForwardDeclarationKind.ObjCProtocol -> ""
        }
        val supertype = when (kind) {
            NativeForwardDeclarationKind.Struct -> "${kind.superClassFqName.asString()}(rawPtr)"
            NativeForwardDeclarationKind.ObjCClass,
            NativeForwardDeclarationKind.ObjCProtocol -> kind.superClassFqName.asString()
        }

        val annotation = "@$cInteropPackage.$ExperimentalForeignApi"
        val classNames = classes.map { it.pathSegments().last().asString() }
        val packageWithImports = """
            package ${pkg.asString()}${System.lineSeparator()}
            
        """.trimIndent()
        val generatedDeclarations = classNames.joinToString(separator = System.lineSeparator()) { className ->
            "$annotation $classKindCodeRepresentation $className $constructor: ${supertype}"
        }
        return packageWithImports + generatedDeclarations
    }

    suspend fun cleanUp(filesToDelete: List<VirtualFile>) {
        if (filesToDelete.isEmpty()) return
        edtWriteAction {
            for (file in filesToDelete) {
                if (file.isValid) {
                    try {
                        file.delete(this)
                    } catch (e: IOException) {
                        LOG.warn(e)
                    }
                }
            }
        }
    }
}
