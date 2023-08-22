// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.core.ec4jwrappers

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.ec4j.core.Resource
import org.ec4j.core.ResourcePath
import org.ec4j.core.model.Ec4jPath
import java.io.Reader

class PurposefullyNotImplementedError
  : Error("Called a method that was purposefully left unimplemented and should never be called")

object NonExistentVirtualFileResource : Resource {
  override fun exists(): Boolean = false

  override fun getParent(): ResourcePath =
    throw PurposefullyNotImplementedError()

  override fun getPath(): Ec4jPath =
    throw PurposefullyNotImplementedError()

  override fun openRandomReader(): Resource.RandomReader =
    throw PurposefullyNotImplementedError()

  override fun openReader(): Reader =
    throw PurposefullyNotImplementedError()
}

class PathHolderResource(val path: String) : Resource {
  override fun exists(): Boolean =
    throw PurposefullyNotImplementedError()

  override fun getParent(): ResourcePath =
    throw PurposefullyNotImplementedError()

  override fun getPath(): Ec4jPath = Ec4jPath.Ec4jPaths.of(path)

  override fun openRandomReader(): Resource.RandomReader =
    throw PurposefullyNotImplementedError()

  override fun openReader(): Reader =
    throw PurposefullyNotImplementedError()
}

class VirtualFileResource(val file: VirtualFile) : Resource, ResourcePath {
  override fun equals(other: Any?): Boolean = other is VirtualFileResource && file == other.file

  override fun hashCode(): Int = file.hashCode()

  override fun exists(): Boolean = file.exists()

  override fun getParent(): ResourcePath? = file.parent?.let { VirtualFileResource(it) }

  override fun getPath(): Ec4jPath = Ec4jPath.Ec4jPaths.of(file.path)

  override fun hasParent(): Boolean = file.parent.let { it != null && it.exists() }

  // Resulting resource is only ever used to getPath for glob pattern matching.
  override fun relativize(resource: Resource): Resource {
    require(resource is VirtualFileResource)
    return PathHolderResource(FileUtil.getRelativePath(this.file.path, resource.file.path, '/', this.file.fileSystem.isCaseSensitive)!!)
  }

  override fun resolve(name: String): Resource =
    VfsUtil.findRelativeFile(file, name)?.let { VirtualFileResource(it) } ?: NonExistentVirtualFileResource

  override fun openRandomReader(): Resource.RandomReader = throw UnsupportedOperationException()

  override fun openReader(): Reader = file.inputStream.bufferedReader(file.charset)
}
