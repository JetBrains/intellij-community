// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LocalFileSystemEelUtil")

package com.intellij.openapi.vfs.impl.local

import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase.LOG
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase.toIoPath
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.fs.*
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.*
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.platform.ijent.community.impl.nio.fsBlocking
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant

private val map = ContainerUtil.createConcurrentWeakMap<Path, EelApi>()

/**
 * [java.nio.file.Files.readAllBytes] takes five separate syscalls to complete.
 * This is unacceptable in the remote setting when each request to IO results in RPC.
 * Here we try to invoke a specialized function that can read all bytes from [path] in one request.
 */
@OptIn(EelDelicateApi::class)
@Suppress("RAW_RUN_BLOCKING")
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

  val api = map.computeIfAbsent(root) {  // TODO Does this cache make sense nowadays?
    runBlocking {
      root.getEelDescriptor().toEelApi()
    }
  }

  val eelPath = path.asEelPath()
  val limit = FileSizeLimit.getContentLoadLimit(FileUtilRt.getExtension(path.fileName.toString()))

  val result = runBlocking {
    try {
      api.fs.readFile(eelPath).limit(limit).failFastIfBeyondLimit(true).getOrThrowFileSystemException()
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

@Suppress("RAW_RUN_BLOCKING")
internal fun fetchCaseSensitivityUsingEel(eelPath: EelPath): FileAttributes.CaseSensitivity {
  val directAccessPath = eelPath.descriptor.mountProvider()?.getMountRoot(eelPath)?.takeIf {
    runBlocking {
      it.canReadPermissionsDirectly(EelMountRoot.DirectAccessOptions.CaseSensitivity)
    }
  }?.transformPath(eelPath)
  val eelPathToCheck: EelPath = if (directAccessPath != null && directAccessPath.descriptor == LocalEelDescriptor) {
    if (Registry.`is`("vfs.fetch.case.sensitivity.using.eel.local")) {
      directAccessPath
    }
    else {
      val ioFile = directAccessPath.parent?.asNioPath()?.toFile()
      return if (ioFile != null) {
        FileSystemUtil.readParentCaseSensitivity(ioFile)
      }
      else {
        FileAttributes.CaseSensitivity.UNKNOWN
      }
    }
  } else {
    eelPath
  }

  return runBlocking {
    val eelApi = eelPathToCheck.descriptor.toEelApi()
    val stat = eelApi.fs.stat(eelPathToCheck).doNotResolve().eelIt().getOr {
      return@runBlocking FileAttributes.CaseSensitivity.UNKNOWN
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

@Throws(IOException::class)
internal fun readAttributesUsingEel(nioPath: Path): FileAttributes {
  val eelDescriptor = nioPath.getEelDescriptor()
  if (eelDescriptor == LocalEelDescriptor) {
    val nioAttributes = Files.readAttributes(nioPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    return FileAttributes.fromNio(nioPath, nioAttributes)
  }
  else {
    val eelPath = nioPath.asEelPath(eelDescriptor)
    val directAccessPath = eelPath.descriptor.mountProvider()?.getMountRoot(eelPath)?.takeIf {
      fsBlocking {
        it.canReadPermissionsDirectly(EelMountRoot.DirectAccessOptions.BasicAttributesAndWritable)
      }
    }?.transformPath(eelPath)
    if (directAccessPath != null && directAccessPath.descriptor == LocalEelDescriptor) {
      val directAccessNioPath = directAccessPath.asNioPath()
      val nioAttributes = Files.readAttributes(directAccessNioPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
      return FileAttributes.fromNio(directAccessNioPath, nioAttributes)
    }
    return fsBlocking {
      when (val eelFsApi = eelPath.descriptor.toEelApi().fs) {
        is EelFileSystemPosixApi -> {
          val fileInfo = eelFsApi.stat(eelPath).eelIt().getOrThrowFileSystemException()
          fileInfo.toVfs(fileInfo.isWritable(eelFsApi))
        }
        else -> TODO()
      }
    }
  }
}

internal fun listWithAttributesUsingEel(
  dir: VirtualFile,
  filter: Set<String>?,
): Map<String, FileAttributes> {
  if (!dir.isDirectory()) {
    return emptyMap()
  }
  try {
    val nioPath = Path.of(toIoPath(dir))
    val eelDescriptor = nioPath.getEelDescriptor()
    if (eelDescriptor === LocalEelDescriptor) {
      return LocalFileSystemImpl.listWithAttributesImpl(nioPath, filter)
    }
    val eelPath = nioPath.asEelPath(eelDescriptor)
    val directAccessPath = eelPath.descriptor.mountProvider()?.getMountRoot(eelPath)?.takeIf {
      fsBlocking {
        it.canReadPermissionsDirectly(EelMountRoot.DirectAccessOptions.BasicAttributesAndWritable)
      }
    }?.transformPath(eelPath)
    if (directAccessPath != null && directAccessPath.descriptor === LocalEelDescriptor) {
      return LocalFileSystemImpl.listWithAttributesImpl(directAccessPath.asNioPath(), filter)
    }

    val expectedSize = filter?.size ?: 10
    //We must return a 'normal' (=case-sensitive) map from this method, see BatchingFileSystem.listWithAttributes() contract:
    val childrenWithAttributes = CollectionFactory.createFilePathMap<FileAttributes>(expectedSize,  /*caseSensitive: */true)

    visitDirectory(eelPath, filter) { file: EelPath, attributes: EelPosixFileInfo, eelFsApi: EelFileSystemPosixApi ->
      try {
        //val attributes = amendAttributes(file, fromNio(file, attributes))
        childrenWithAttributes[file.fileName] = attributes.toVfs(attributes.isWritable(eelFsApi))
      }
      catch (e: Exception) {
        LOG.debug(e)
      }
      true
    }

    return childrenWithAttributes
  }
  catch (e: AccessDeniedException) {
    LOG.debug(e)
  }
  catch (e: NoSuchFileException) {
    LOG.debug(e)
  }
  catch (e: IOException) {
    LOG.warn(e)
  }
  catch (e: RuntimeException) {
    LOG.warn(e)
  }
  return emptyMap()
}

@Throws(IOException::class, SecurityException::class)
private fun visitDirectory(
  directory: EelPath,
  filter: Set<String>?,
  consumer: (EelPath, EelPosixFileInfo, EelFileSystemPosixApi) -> Boolean,
) {
  if (filter != null && filter.isEmpty()) {
    return  //nothing to read
  }
  fsBlocking {
    return@fsBlocking when (val eelFsApi = directory.descriptor.toEelApi().fs) {
      is EelFileSystemPosixApi -> {
        val directoryList = eelFsApi.listDirectoryWithAttrs(directory).symlinkPolicy(EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW).eelIt().getOrThrowFileSystemException()
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
      else -> TODO()
    }
  }
}

fun EelPosixFileInfo.isWritable(eelFsApi: EelFileSystemPosixApi): Boolean {
  return EelPathUtils.checkAccess(eelFsApi.user, this, AccessMode.WRITE) == null
}

fun EelFileInfo.toVfs(isWritable: Boolean): FileAttributes {
  val attrs = this

  val isDirectory = attrs.type is EelFileInfo.Type.Directory
  val isSpecial = attrs.type is EelFileInfo.Type.Other
  val isSymLink = attrs.type is EelPosixFileInfo.Type.Symlink
  val isHidden = false
  val length = (attrs.type as? EelFileInfo.Type.Regular)?.size ?: 0
  val lastModified = FileTime.from(attrs.lastModifiedTime?.toInstant() ?: Instant.MIN).toMillis()
  val caseSensitivity = when (val type = attrs.type) {
    is EelFileInfo.Type.Directory -> EelPathUtils.getCaseSensitivity(type)
    else -> FileAttributes.CaseSensitivity.UNKNOWN
  }

  return FileAttributes(isDirectory, isSpecial, isSymLink, isHidden, length, lastModified, isWritable, caseSensitivity)
}