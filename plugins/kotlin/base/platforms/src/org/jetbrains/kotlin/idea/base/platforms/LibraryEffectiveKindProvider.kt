// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.UnknownLibraryKind
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.indexing.roots.IndexableFileScanner
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragment

@Service(Service.Level.PROJECT)
class LibraryEffectiveKindProvider {
    private companion object {
        val LIBRARY_KIND_KEY = Key.create<PersistentLibraryKind<*>?>("LibraryEffectiveKind")
        val NEEDS_TO_BE_CLARIFIED_KIND: UnknownLibraryKind = UnknownLibraryKind.getOrCreate("Needs to be clarified")
    }

    private enum class KnownLibraryKindForIndex {
        COMMON, JS, UNKNOWN
    }

    fun getEffectiveKind(library: Library): PersistentLibraryKind<*>? {
        library as LibraryEx

        if (library.isDisposed) {
            return null
        }

        return when (val kind = library.kind) {
            is KotlinLibraryKind -> kind
            else -> {
                val classRoots = library.getFiles(OrderRootType.CLASSES)
                val classRoot = classRoots.firstOrNull() ?: return null

                var platformKind = classRoot.getUserData(LIBRARY_KIND_KEY)

                if (platformKind == null) {
                    LibraryKindScanner.runScannerOutsideScanningSession(classRoots)
                    platformKind = classRoot.getUserData(LIBRARY_KIND_KEY)
                }

                if (platformKind == NEEDS_TO_BE_CLARIFIED_KIND) {
                    val matchingPlatformKind = IdePlatformKindProjectStructure.getLibraryPlatformKind(classRoot)
                        ?: JvmPlatforms.defaultJvmPlatform.idePlatformKind

                    val detectedKind = IdePlatformKindProjectStructure.getLibraryKind(matchingPlatformKind)
                    classRoot.putUserData(LIBRARY_KIND_KEY, detectedKind)
                    return detectedKind
                }

                return platformKind
            }
        }
    }

    // it's assumed that this scanner runs everytime when library has been changed and library kind will be updated
    class LibraryKindScanner : IndexableFileScanner {
        internal companion object {
            fun runScannerOutsideScanningSession(classRoots: Array<VirtualFile>) {
                val scannerVisitor = ScannerVisitor()
                for (classRoot in classRoots) {
                    VfsUtil.visitChildrenRecursively(classRoot, object : VirtualFileVisitor<Any?>() {
                        override fun visitFile(file: VirtualFile): Boolean {
                            scannerVisitor.visitFile(file)
                            return true
                        }
                    })
                }
                scannerVisitor.visitingFinished(classRoots.toList())
            }
        }

        private class ScannerVisitor {
            val classFileType = FileTypeRegistry.getInstance().getFileTypeByExtension("class")
            val kotlinJavaScriptMetaFileType = FileTypeRegistry.getInstance().getFileTypeByExtension("kjsm")

            var result: KnownLibraryKindForIndex? = null

            fun visitFile(fileOrDir: VirtualFile) {
                if (result != KnownLibraryKindForIndex.UNKNOWN) {
                    val fileType = fileOrDir.fileType
                    when {
                        fileType == classFileType ->
                            result = KnownLibraryKindForIndex.UNKNOWN
                        fileType == kotlinJavaScriptMetaFileType ->
                            result = KnownLibraryKindForIndex.JS
                        fileType == KotlinBuiltInFileType
                                && fileOrDir.extension == MetadataPackageFragment.METADATA_FILE_EXTENSION
                                && result == null ->
                            result = KnownLibraryKindForIndex.COMMON
                        else -> Unit
                    }
                }
            }

            fun visitingFinished(roots: Collection<VirtualFile>) {
                val platformKind = when (result) {
                    KnownLibraryKindForIndex.COMMON -> KotlinCommonLibraryKind
                    KnownLibraryKindForIndex.JS -> KotlinJavaScriptLibraryKind
                    else -> NEEDS_TO_BE_CLARIFIED_KIND
                }

                for (classRoot in roots) {
                    classRoot.putUserData(LIBRARY_KIND_KEY, platformKind)
                }
            }
        }

        override fun startSession(project: Project): IndexableFileScanner.ScanSession {
            val scannerVisitor = ScannerVisitor()
            return object : IndexableFileScanner.ScanSession {
                override fun createVisitor(indexableSetOrigin: IndexableSetOrigin): IndexableFileScanner.IndexableFileVisitor? {
                    if (indexableSetOrigin is LibraryOrigin) {
                        return object : IndexableFileScanner.IndexableFileVisitor {
                            override fun visitFile(fileOrDir: VirtualFile) = scannerVisitor.visitFile(fileOrDir)

                            override fun visitingFinished() {
                                val roots = indexableSetOrigin.classRoots
                                scannerVisitor.visitingFinished(roots)
                            }
                        }
                    }
                    return null
                }
            }
        }
    }
}
