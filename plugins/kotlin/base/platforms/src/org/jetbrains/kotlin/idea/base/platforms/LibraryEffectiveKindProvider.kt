// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryKindRegistry
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.VirtualFileGist
import com.intellij.util.indexing.roots.IndexableFileScanner
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.IOUtil
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.serialization.deserialization.DOT_METADATA_FILE_EXTENSION
import java.io.DataInput
import java.io.DataOutput
import java.util.concurrent.ConcurrentHashMap

private enum class KnownLibraryKindForIndex {
    COMMON, JS, UNKNOWN
}

interface LibraryEffectiveKindProvider {
    fun getEffectiveKind(library: Library): PersistentLibraryKind<*>?
}

class LibraryEffectiveKindProviderImpl(private val project: Project): LibraryEffectiveKindProvider {
    private fun findKind(classRoots: Array<VirtualFile>): PersistentLibraryKind<*>? {
        val virtualFile = classRoots.firstOrNull() ?: return null
        virtualFile.putUserData(CLASS_ROOTS_KEY, classRoots)
        try {
            return runReadAction {
                KotlinLibraryKindGistProvider.getInstance().kotlinLibraryKindGist.getFileData(project, virtualFile)
            }
        } finally {
            virtualFile.removeUserData(CLASS_ROOTS_KEY)
        }
    }

    override fun getEffectiveKind(library: Library): PersistentLibraryKind<*>? {
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
                    classRoot.getUserData(LIBRARY_KIND_KEY) ?: findKind(classRoots)?.let {
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
            fun runScannerOutsideScanningSession(classRoot: VirtualFile) {
                val scannerVisitor = ScannerVisitor()
                VfsUtil.visitChildrenRecursively(classRoot, object : VirtualFileVisitor<Any?>() {
                    override fun visitFileEx(file: VirtualFile): Result =
                        if (visitFile(file)) CONTINUE else skipTo(classRoot)

                    override fun visitFile(file: VirtualFile): Boolean {
                        ProgressManager.checkCanceled()
                        scannerVisitor.visitFile(file)
                        val knownLibraryKind = scannerVisitor.knownLibraryKindForClassRoot[classRoot]
                        return knownLibraryKind == null || knownLibraryKind == KnownLibraryKindForIndex.COMMON
                    }
                })
                scannerVisitor.visitingFinished()
            }
        }

        private class ScannerVisitor {
            val classFileType = FileTypeRegistry.getInstance().getFileTypeByExtension("class")
            val kotlinJavaScriptMetaFileType = FileTypeRegistry.getInstance().getFileTypeByExtension("kjsm")
            val jarFileSystem: JarFileSystem = JarFileSystem.getInstance()
            val fileTypeManager: FileTypeManager = FileTypeManager.getInstance()

            val knownLibraryKindForClassRoot = ConcurrentHashMap<VirtualFile, KnownLibraryKindForIndex>()

            fun visitFile(fileOrDir: VirtualFile) {
                if (fileOrDir.isDirectory) return

                val classRoot = jarFileSystem.getRootByEntry(fileOrDir) ?: return
                val kind = knownLibraryKindForClassRoot[classRoot]

                if (kind != KnownLibraryKindForIndex.UNKNOWN) {
                    val name = fileOrDir.name
                    if (name.endsWith(".java") ||
                        name.endsWith(KotlinFileType.DOT_DEFAULT_EXTENSION)
                    ) return

                    val fileType = when {
                        name.endsWith(".class") -> classFileType
                        else -> {
                            val extension = name.substringAfterLast(".", "")
                            val fileTypeByExtension = fileTypeManager.getFileTypeByExtension(extension)
                            fileTypeByExtension
                        }
                    }
                    when {
                        fileType == classFileType ->
                            knownLibraryKindForClassRoot[classRoot] = KnownLibraryKindForIndex.UNKNOWN

                        fileType == kotlinJavaScriptMetaFileType ->
                            knownLibraryKindForClassRoot[classRoot] = KnownLibraryKindForIndex.JS

                        kind == null &&
                                (fileType == KlibMetaFileType ||
                                        name.endsWith(DOT_METADATA_FILE_EXTENSION) && fileType == KotlinBuiltInFileType) ->
                            knownLibraryKindForClassRoot[classRoot] = KnownLibraryKindForIndex.COMMON

                        else -> Unit
                    }
                }
            }

            fun visitingFinished() {
                for ((classRoot, knownLibraryKind) in knownLibraryKindForClassRoot) {
                    classRoot.putUserData(LIBRARY_KIND_KEY, null)  // Clear outdated cache
                    classRoot.putUserData(LIBRARY_KIND_GUESS_FROM_CONTENT_KEY, knownLibraryKind)
                }
            }
        }

        override fun startSession(project: Project): IndexableFileScanner.ScanSession =
            IndexableFileScanner.ScanSession { indexableSetOrigin ->
                if (indexableSetOrigin is LibraryOrigin) {
                    object : IndexableFileScanner.IndexableFileVisitor {
                        private val scannerVisitor = ScannerVisitor()

                        override fun visitFile(fileOrDir: VirtualFile) {
                            scannerVisitor.visitFile(fileOrDir)
                        }

                        override fun visitingFinished() {
                            scannerVisitor.visitingFinished()
                        }
                    }
                } else {
                    null
                }
            }
    }

}

@Service(Service.Level.APP)
private class KotlinLibraryKindGistProvider {
    val kotlinLibraryKindGist: VirtualFileGist<PersistentLibraryKind<*>> = createGist()

    private fun createGist(): VirtualFileGist<PersistentLibraryKind<*>> = GistManager.getInstance().newVirtualFileGist(
        "kotlin-library-kind",
        2,
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
        // obtain platform from `/*/manifest` file from klib-like archive
        var platformKind: PersistentLibraryKind<*>? = file.takeIf { it.isKLibRootCandidate() }
            ?.let { IdePlatformKindProjectStructure.getLibraryPlatformKind(it) }
            ?.let { IdePlatformKindProjectStructure.getLibraryKind(it) }

        // otherwise try to guess the platform based on content of archive - does it have js specific files, or common
        if (platformKind == null) {
            platformKind = guessLibraryKindFromContent(classRoots)
        }

        // as the final resort - we consider them as a regular jar JVM library
        if (platformKind == null) {
            IdePlatformKindProjectStructure.getLibraryKind(JvmPlatforms.defaultJvmPlatform.idePlatformKind)
        }
        platformKind
    }

    private fun guessLibraryKindFromContent(classRoots: Array<VirtualFile>): PersistentLibraryKind<*>? {
        val classRootLibraryKinds = classRoots.asSequence().map { classRoot ->
            classRoot.getUserData(LIBRARY_KIND_GUESS_FROM_CONTENT_KEY)?.let { return@map it }
            // Library hasn't been scanned yet - run the scanner manually
            LibraryEffectiveKindProviderImpl.LibraryKindScanner.runScannerOutsideScanningSession(classRoot)
            classRoot.getUserData(LIBRARY_KIND_GUESS_FROM_CONTENT_KEY)
        }

        var hasCommonLibraries = false
        for (libraryKind in classRootLibraryKinds) {
            when (libraryKind) {
                KnownLibraryKindForIndex.JS -> return KotlinJavaScriptLibraryKind
                null, KnownLibraryKindForIndex.UNKNOWN -> return null
                // A JVM library can have common jars inside, but it's not a reason to immediately say it's a common library.
                // First check there are no UNKNOWNs and only then proceed.
                KnownLibraryKindForIndex.COMMON -> hasCommonLibraries = true
            }
        }
        return if (hasCommonLibraries) {
            KotlinCommonLibraryKind
        } else {
            null
        }
    }

    companion object {
        fun getInstance(): KotlinLibraryKindGistProvider = ApplicationManager.getApplication().service()
    }
}

private val LIBRARY_KIND_KEY: Key<PersistentLibraryKind<*>> = Key.create("LibraryEffectiveKind")
private val LIBRARY_KIND_GUESS_FROM_CONTENT_KEY: Key<KnownLibraryKindForIndex> = Key.create("LibraryEffectiveKindGuessFromContent")
private val CLASS_ROOTS_KEY: Key<Array<VirtualFile>> = Key.create("LibraryClassRoots")
