// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LocalFileSystemEelUtil")
package com.intellij.openapi.vfs.impl.local

import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelFileSystemWindowsApi
import com.intellij.platform.eel.fs.EelPosixFileInfo
import com.intellij.platform.eel.fs.EelWindowsFileInfo
import com.intellij.platform.eel.fs.listDirectoryWithAttrs
import com.intellij.platform.eel.fs.readFile
import com.intellij.platform.eel.fs.stat
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.nioFs.impl.utils.getCaseSensitivity
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.EelMountProvider
import com.intellij.platform.eel.provider.EelMountRoot
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.PrefetchContextBuilder
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.canReadPermissionsDirectly
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.transformPath
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.platform.ijent.community.impl.nio.fsBlocking
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.io.toByteArray
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.AccessMode
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant

//Eel-specific optimizations: mostly about reducing the number of Eel network calls with batching.
// The methods are not to be used outside the package.
// Most of the methods should have been (private) methods in [LocalFileSystemImpl]
// (Probably, extracted here just to use Kotlin?)

@ApiStatus.Internal
@VisibleForTesting
@Throws(IOException::class)
fun readAttributesUsingEel(nioPath: Path): FileAttributes {
  val eelDescriptor = nioPath.getEelDescriptor()
  if (eelDescriptor == LocalEelDescriptor) {
    val nioAttributes = Files.readAttributes(nioPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    return FileAttributes.fromNio(nioPath, nioAttributes)
  }
  else {
    @OptIn(EelDelicateApi::class)
    val eelPath = nioPath.asEelPath()
    val directAccessPath = (nioPath.fileSystem as? EelMountProvider)?.getMountRoot(eelPath)?.takeIf {
      eelPath.fsBlocking {
        it.canReadPermissionsDirectly(EelMountRoot.DirectAccessOptions.BasicAttributesAndWritable)
      }
    }?.transformPath(eelPath)
    if (directAccessPath != null && directAccessPath.descriptor == LocalEelDescriptor) {
      val directAccessNioPath = directAccessPath.asNioPath()
      val nioAttributes = Files.readAttributes(directAccessNioPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
      return FileAttributes.fromNio(directAccessNioPath, nioAttributes)
    }
    return eelPath.fsBlocking {
      // stale persisted descriptor (machine deregistered) - surface as not-found (IJPL-245202)
      val machine = eelPath.descriptor.getResolvedEelMachine() ?: throw NoSuchFileException(nioPath.toString())
      val eelFsApi = machine.toEelApi(eelPath.descriptor).fs
      val fileInfo = eelFsApi.stat(eelPath).eelIt().getOrThrowFileSystemException()
      toVfs(eelPath, fileInfo, eelFsApi)
    }
  }
}

@ApiStatus.Internal
fun getAttributeListingPaths(nioPath: Path): Pair<Path?, EelPath?> {
  val eelDescriptor = nioPath.getEelDescriptor()
  if (eelDescriptor === LocalEelDescriptor) {
    return nioPath to null
    }
    @OptIn(EelDelicateApi::class)
  val eelPath = nioPath.asEelPath()
  val directAccessPath = (nioPath.fileSystem as? EelMountProvider)
    ?.getMountRoot(eelPath)
    ?.takeIf { eelPath.fsBlocking { it.canReadPermissionsDirectly(EelMountRoot.DirectAccessOptions.BasicAttributesAndWritable) } }
    ?.transformPath(eelPath)
  if (directAccessPath != null && directAccessPath.descriptor === LocalEelDescriptor) {
    return directAccessPath.asNioPath() to null
  }

  return null to eelPath
}

@ApiStatus.Internal
@VisibleForTesting
@Throws(IOException::class)
fun listWithAttributesUsingEel(eelPath: EelPath, filter: Set<String>?): Map<String, FileAttributes> {
  val expectedSize = filter?.size ?: 10
  // we must return a 'normal' (=case-sensitive) map from this method (see the [BatchingFileSystem#listWithAttributes] contract)
  val childrenWithAttributes = CollectionFactory.createFilePathMap<FileAttributes>(expectedSize,  /*caseSensitive: */true)

  visitDirectory(eelPath, filter) { file: EelPath, attributes: EelFileInfo, eelFsApi: EelFileSystemApi ->
    try {
      val childAttributes = toVfs(file, attributes, eelFsApi)
      childrenWithAttributes[file.fileName] = amendAttributes(childAttributes) { file.asNioPath() }
    }
    catch (e: Exception) {
      @Suppress("removal", "DEPRECATION")
      LocalFileSystemBase.LOG.debug(e)
    }
    true
  }

  return childrenWithAttributes
}

/**
 * Prefetches remote directory trees for VFS refresh and runs [block] with the
 * prefetch cache installed in the thread context. The cache propagates automatically
 * to child threads via [com.intellij.concurrency.IntelliJContextElement] (through context-propagating executors).
 *
 * If no remote roots are found among [roots], [block] is called directly without prefetching.
 */
@ApiStatus.Internal
fun withPrefetchForRemoteRoots(roots: Collection<@JvmWildcard VirtualFile>, block: () -> Unit) {
  if (!Registry.`is`("vfs.eel.scanning.prefetch.enabled", true)) {
    block()
    return
  }
  val remoteRoots = roots.mapNotNull { root ->
    try {
      val nioPath = root.fileSystem.getNioPath(root) ?: return@mapNotNull null
      val descriptor = nioPath.getEelDescriptor()
      if (descriptor === LocalEelDescriptor) return@mapNotNull null
        @OptIn(EelDelicateApi::class)
      val eelPath = nioPath.asEelPath()
      // skip FS root — prefetching entire remote filesystem is wasteful; VFS refresh from root only checks cached children anyway
      if (eelPath.parent == null) return@mapNotNull null
      // skip paths with direct local mount — they bypass gRPC entirely
      if ((nioPath.fileSystem as? EelMountProvider)?.getMountRoot(eelPath) != null) return@mapNotNull null
      descriptor to eelPath
    }
    catch (_: Exception) {
      null
    }
  }
  if (remoteRoots.isEmpty()) {
    block()
    return
  }

  val element = try {
    PrefetchContextBuilder(remoteRoots).apply {
      rootsByDescriptor.keys.forEach { descriptor ->
        descriptor.fsBlocking {
          prefetchForDescriptor(descriptor)
        }
      }
    }.toElement()
  }
  catch (e: Exception) {
    @Suppress("removal", "DEPRECATION")
    LocalFileSystemBase.LOG.warn("Failed to prefetch remote roots for VFS refresh", e)
    null
  }
  if (element != null) {
    @Suppress("removal", "DEPRECATION")
    LocalFileSystemBase.LOG.info("VFS refresh prefetch: ${element.size} directories cached for ${remoteRoots.size} remote roots")
    val context = com.intellij.concurrency.currentThreadContext() + element
    com.intellij.concurrency.installThreadContext(context, replace = true, block)
  }
  else {
    block()
  }
}

/**
 * [java.nio.file.Files.readAllBytes] takes five separate syscalls to complete.
 * This is unacceptable in the remote setting when each request to IO results in RPC.
 * Here we try to invoke a specialized function that can read all bytes from [path] in one request.
 */
@OptIn(EelDelicateApi::class)
internal fun readWholeFileIfNotTooLargeWithEel(path: Path): ByteArray? {
  if (!Registry.`is`("vfs.try.eel.for.content.loading", false)) {
    return null
  }
  val root = path.root ?: return null

  // TODO Check if this if-else can be removed. The only reason why it's kept is to avoid possible performance degradations in hot code.
  val eelDescriptor = root.getEelDescriptor()
  if (eelDescriptor == LocalEelDescriptor) {
    return null
  }

  val eelPath = path.asEelPath()

  if ((path.fileSystem as? EelMountProvider)?.getMountRoot(eelPath) != null) {
    return null
  }

  val limit = FileSizeLimit.getContentLoadLimit(FileUtilRt.getExtension(path.fileName.toString()))

  val result = eelDescriptor.fsBlocking {
    try {
      val eelApi = eelDescriptor.toEelApi()
      eelApi.fs.readFile(eelPath).limit(limit).failFastIfBeyondLimit(true).getOrThrowFileSystemException()
    }
    catch (err: FileSystemException) {
      throw err.cause.takeIf { it is FileTooBigException } ?: err
    }
  }

  return result.bytes.toByteArray()
}

internal fun toEelPath(parent: VirtualFile, childName: String): EelPath? =
  try {
    parent.toNioPath().resolve(childName).asEelPath()
  }
  catch (err: Exception) {
    when (err) {
      is UnsupportedOperationException, is InvalidPathException, is EelPathException -> null
      else -> throw err
    }
  }

internal fun fetchCaseSensitivityUsingEel(eelPath: EelPath): FileAttributes.CaseSensitivity {
  val directAccessPath = (eelPath.asNioPath().fileSystem as? EelMountProvider)?.getMountRoot(eelPath)?.takeIf {
    eelPath.fsBlocking {
      it.canReadPermissionsDirectly(EelMountRoot.DirectAccessOptions.CaseSensitivity)
    }
  }?.transformPath(eelPath)
  val eelPathToCheck: EelPath = if (directAccessPath != null && directAccessPath.descriptor == LocalEelDescriptor) {
    if (Registry.`is`("vfs.fetch.case.sensitivity.using.eel.local")) {
      directAccessPath
    }
    else {
      val nioPath = directAccessPath.parent?.asNioPath()
      return if (nioPath != null) {
        FileSystemUtil.readParentCaseSensitivity(nioPath)
      }
      else {
        FileAttributes.CaseSensitivity.UNKNOWN
      }
    }
  }
  else {
    eelPath
  }

  return eelPathToCheck.fsBlocking {
    val eelApi = eelPathToCheck.descriptor.toEelApi()
    val stat = eelApi.fs.stat(eelPathToCheck).doNotResolve().eelIt().getOr {
      return@fsBlocking FileAttributes.CaseSensitivity.UNKNOWN
    }

    when (val type = stat.type) {
      is EelFileInfo.Type.Directory ->
        when (type.sensitivity) {
          EelFileInfo.CaseSensitivity.SENSITIVE -> FileAttributes.CaseSensitivity.SENSITIVE
          EelFileInfo.CaseSensitivity.INSENSITIVE -> FileAttributes.CaseSensitivity.INSENSITIVE
          EelFileInfo.CaseSensitivity.UNKNOWN -> FileAttributes.CaseSensitivity.UNKNOWN
        }

      is EelFileInfo.Type.Other, is EelFileInfo.Type.Regular, is EelPosixFileInfo.Type.Symlink ->
        FileAttributes.CaseSensitivity.UNKNOWN
    }
  }
}

private suspend fun toVfs(eelPath: EelPath, eelFileInfo: EelFileInfo, eelFsApi: EelFileSystemApi): FileAttributes {
  val resolvedFileInfo = if (eelFileInfo.type is EelPosixFileInfo.Type.Symlink) {
    eelFsApi
      .stat(eelPath)
      .symlinkPolicy(EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW)
      .eelIt().getOrNull() ?: return FileAttributes.BROKEN_SYMLINK
  }
  else {
    eelFileInfo
  }

  val isSymLink = eelFileInfo.type is EelPosixFileInfo.Type.Symlink
  return when (eelFsApi) {
    is EelFileSystemPosixApi if resolvedFileInfo is EelPosixFileInfo -> {
      resolvedFileInfo.toVfs(resolvedFileInfo.isWritable(eelFsApi), isSymLink)
    }
    is EelFileSystemWindowsApi if resolvedFileInfo is EelWindowsFileInfo -> {
      resolvedFileInfo.toVfs(!resolvedFileInfo.permissions.isReadOnly, isSymLink)
    }
    else -> error("EelFileInfo ${resolvedFileInfo} does not belong to EelFileSystemApi ${eelFsApi}")
  }
}

internal fun amendAttributes(file: Path, attributes: FileAttributes): FileAttributes {
  return amendAttributes(attributes) { file }
}

private inline fun amendAttributes(attributes: FileAttributes, file: () -> Path): FileAttributes {
  for (provider in LocalFileSystemTimestampEvaluator.EP_NAME.extensionList) {
    val customTS = provider.getTimestamp(file())
    if (customTS != null) {
      return attributes.withLastModified(customTS)
    }
  }
  return attributes
}

@Throws(IOException::class, SecurityException::class)
private fun visitDirectory(
  directory: EelPath,
  filter: Set<String>?,
  consumer: suspend (EelPath, EelFileInfo, EelFileSystemApi) -> Boolean,
) {
  if (filter != null && filter.isEmpty()) {
    return  //nothing to read
  }
  directory.fsBlocking {
    val eelFsApi = directory.descriptor.toEelApi().fs
    val directoryList =
      eelFsApi.listDirectoryWithAttrs(directory).symlinkPolicy(EelFileSystemApi.SymlinkPolicy.DO_NOT_RESOLVE).eelIt()
        .getOrThrowFileSystemException()
    for ((childName, childStat) in directoryList) {
      val childIjentPath = directory.getChild(childName)
      if (filter != null && !filter.contains(childIjentPath.fileName)) {
        continue
      }
      if (!consumer(childIjentPath, childStat, eelFsApi)) {
        break
      }
    }
  }
}

private fun EelPosixFileInfo.isWritable(eelFsApi: EelFileSystemPosixApi): Boolean {
  return EelPathUtils.checkAccess(eelFsApi.user, this, AccessMode.WRITE) == null
}

private fun EelFileInfo.toVfs(isWritable: Boolean, isSymLink: Boolean): FileAttributes {
  val attrs = this

  val isDirectory = attrs.type is EelFileInfo.Type.Directory
  val isSpecial = attrs.type is EelFileInfo.Type.Other
  val isHidden = false
  val length = (attrs.type as? EelFileInfo.Type.Regular)?.size ?: 0
  val lastModified = FileTime.from(attrs.lastModifiedTime?.toInstant() ?: Instant.MIN).toMillis()
  val caseSensitivity = when (val type = attrs.type) {
    is EelFileInfo.Type.Directory -> type.getCaseSensitivity()
    else -> FileAttributes.CaseSensitivity.UNKNOWN
  }

  return FileAttributes(isDirectory, isSpecial, isSymLink, isHidden, length, lastModified, isWritable, caseSensitivity)
}
