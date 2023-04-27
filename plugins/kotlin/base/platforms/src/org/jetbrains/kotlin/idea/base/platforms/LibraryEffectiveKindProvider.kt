// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.UnknownLibraryKind
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryKindRegistry
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.VirtualFileGist
import com.intellij.util.indexing.roots.IndexableFileScanner
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.io.*
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragment
import java.io.DataInput
import java.io.DataOutput

@Service(Service.Level.PROJECT)
class LibraryEffectiveKindProvider(private val project: Project) {
    private companion object {
        val LIBRARY_KIND_KEY: Key<PersistentLibraryKind<*>> = Key.create("LibraryEffectiveKind")
        val CLASS_ROOTS_KEY: Key<Array<VirtualFile>> = Key.create("LibraryClassRoots")
        val NEEDS_TO_BE_CLARIFIED_KIND: UnknownLibraryKind = UnknownLibraryKind.getOrCreate("Needs to be clarified")

        @JvmStatic
        private val KOTLIN_LIBRARY_KIND_GIST: VirtualFileGist<PersistentLibraryKind<*>> = GistManager.getInstance().newVirtualFileGist(
            "kotlin-library-kind",
            1,
            object : DataExternalizer<PersistentLibraryKind<*>> {
                override fun save(out: DataOutput, value: PersistentLibraryKind<*>) {
                    val kindId = value.kindId
                    IOUtil.writeString(kindId, out)
                }

                override fun read(`in`: DataInput): PersistentLibraryKind<*>? =
                    when (val kindId = IOUtil.readString(`in`)) {
                        // as KotlinJvmEffectiveLibraryKind is a fake library kind
                        KotlinJvmEffectiveLibraryKind.kindId -> KotlinJvmEffectiveLibraryKind
                        else -> LibraryKindRegistry.getInstance().findKindById(kindId) as? PersistentLibraryKind<*>
                    }
            }
        ) { _, file ->
            val classRoots = file.getUserData(CLASS_ROOTS_KEY) ?: arrayOf(file)
            LibraryKindScanner.runScannerOutsideScanningSession(classRoots)
            var platformKind: PersistentLibraryKind<*>? = file.getUserData(LIBRARY_KIND_KEY)
            if (platformKind == NEEDS_TO_BE_CLARIFIED_KIND) {
                val matchingPlatformKind = IdePlatformKindProjectStructure.getLibraryPlatformKind(file)
                    ?: JvmPlatforms.defaultJvmPlatform.idePlatformKind
                platformKind = IdePlatformKindProjectStructure.getLibraryKind(matchingPlatformKind)
            }
            platformKind
        }

    }

    private enum class KnownLibraryKindForIndex {
        COMMON, JS, UNKNOWN
    }

    private fun findKind(classRoots: Array<VirtualFile>): PersistentLibraryKind<*>? {
        val virtualFile = classRoots.firstOrNull() ?: return null
        virtualFile.putUserData(CLASS_ROOTS_KEY, classRoots)
        try {
            return runReadAction { KOTLIN_LIBRARY_KIND_GIST.getFileData(project, virtualFile) }
        } finally {
            virtualFile.removeUserData(CLASS_ROOTS_KEY)
        }
    }

    fun getEffectiveKind(library: Library): PersistentLibraryKind<*>? {
        require(library is LibraryEx)

        if (library.isDisposed) {
            return null
        }

        return when (val kind = library.kind) {
            is KotlinLibraryKind -> kind
            else -> {
                val classRoots = library.getFiles(OrderRootType.CLASSES)
                val classRoot = classRoots.firstOrNull() ?: return null

                val platformKind: PersistentLibraryKind<*>? =
                    classRoot.getUserData(LIBRARY_KIND_KEY)?.takeIf { it != NEEDS_TO_BE_CLARIFIED_KIND } ?:
                    findKind(classRoots)?.let {
                        classRoot.putUserData(LIBRARY_KIND_KEY, it)
                        it
                    }

                val nonJvmOrNull = platformKind?.nonJvmOrNull()
                nonJvmOrNull
            }
        }
    }

    // Kotlin JVM kind is expected to be cached to prevent reevaluation, but is not expected to be exposed to not break non-Kotlin kinds
    private fun PersistentLibraryKind<*>.nonJvmOrNull(): PersistentLibraryKind<*>? = this.takeIf { it != KotlinJvmEffectiveLibraryKind }

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
