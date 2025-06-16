// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NonNls
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.Paths

const val KOTLIN_FORWARD_DECLARATIONS_PROTOCOL = "fwd"
private const val KOTLIN_FORWARD_DECLARATIONS_GENERATED_FILES_DIR = "kotlin-native-forward-declarations"
private const val VERSION = 1

/**
 * [com.intellij.openapi.vfs.VirtualFileSystem] to hide storage implementation details for K/N forward declarations.
 */
abstract class KotlinForwardDeclarationsFileSystem : NewVirtualFileSystem() {
    companion object {
        fun getInstance(): KotlinForwardDeclarationsFileSystem =
            VirtualFileManager.getInstance().getFileSystem(KOTLIN_FORWARD_DECLARATIONS_PROTOCOL) as KotlinForwardDeclarationsFileSystem

        val storageRootPath: Path
            get() = Paths.get(PathManager.getSystemPath(), KOTLIN_FORWARD_DECLARATIONS_GENERATED_FILES_DIR, "v$VERSION")
    }
}

/**
 * Initially added for hiding storage details under abstraction:
 * ```
 * fwd://<manifest_path>/!<package> -> file://<storage_root>/<manifest_path>/<package>
 * ```
 * However, the fwd protocol is not in use at the moment.
 */
// TODO: KTIJ-29679 rethink the design or drop altogether.
// TODO: KTIJ-29679 potential problem with long paths after nesting under the storage root.
internal class KotlinForwardDeclarationsFileSystemImpl : KotlinForwardDeclarationsFileSystem() {
    override fun extractRootPath(normalizedPath: String): String {
        return normalizedPath // TODO: KTIJ-29679
    }

    override fun findFileByPathIfCached(path: @NonNls String): VirtualFile? {
        return findFileByPath(path)
    }

    override fun getRank(): Int {
        return 1
    }

    override fun copyFile(
        requestor: Any?,
        file: VirtualFile,
        newParent: VirtualFile,
        copyName: String
    ): VirtualFile {
        throw UnsupportedOperationException()
    }

    override fun createChildDirectory(
        requestor: Any?,
        parent: VirtualFile,
        dir: String
    ): VirtualFile {
        throw UnsupportedOperationException()
    }

    override fun createChildFile(
        requestor: Any?,
        parent: VirtualFile,
        file: String
    ): VirtualFile {
        throw UnsupportedOperationException()
    }

    override fun deleteFile(requestor: Any?, file: VirtualFile) {
        throw UnsupportedOperationException()
    }

    override fun moveFile(
        requestor: Any?,
        file: VirtualFile,
        newParent: VirtualFile
    ) {
        throw UnsupportedOperationException()
    }

    override fun renameFile(requestor: Any?, file: VirtualFile, newName: String) {
        throw UnsupportedOperationException()
    }

    override fun getAttributes(file: VirtualFile): FileAttributes {
        TODO("KTIJ-29679")
    }

    override fun getProtocol(): @NonNls String {
        return KOTLIN_FORWARD_DECLARATIONS_PROTOCOL
    }

    override fun findFileByPath(path: @NonNls String): VirtualFile? {
        val unoptimizedLocalPath = storageRootPath.resolve(path.drop(1))
        val file = VfsUtil.findFile(unoptimizedLocalPath, false)
        return file
    }

    override fun refresh(asynchronous: Boolean) {
        // TODO KTIJ-29679
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? {
        return findFileByPath(path)
    }

    override fun exists(file: VirtualFile): Boolean {
        TODO("KTIJ-29679")
    }

    override fun list(file: VirtualFile): Array<out String?> {
        return emptyArray()
    }

    override fun isDirectory(file: VirtualFile): Boolean {
        return false
    }

    override fun getTimeStamp(file: VirtualFile): Long {
        throw IncorrectOperationException()
    }

    override fun setTimeStamp(file: VirtualFile, timeStamp: Long) {
        throw IncorrectOperationException()
    }

    override fun isWritable(file: VirtualFile): Boolean {
        return false
    }

    override fun setWritable(file: VirtualFile, writableFlag: Boolean) {
        throw IncorrectOperationException()
    }

    override fun contentsToByteArray(file: VirtualFile): ByteArray {
        return ByteArray(0)
    }

    override fun getInputStream(file: VirtualFile): InputStream {
        TODO("KTIJ-29679")
    }

    override fun getOutputStream(
        file: VirtualFile,
        requestor: Any?,
        modStamp: Long,
        timeStamp: Long
    ): OutputStream {
        TODO("KTIJ-29679")
    }

    override fun getLength(file: VirtualFile): Long {
        TODO("KTIJ-29679")
    }
}
