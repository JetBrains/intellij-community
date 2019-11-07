package com.intellij.workspace.api

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger

object VirtualFileUrlManager {
  private val id2parent: ConcurrentMap<Int, Int> = ConcurrentHashMap()
  private val id2segment: ConcurrentMap<Int, String> = ConcurrentHashMap()
  private val parent2id: ConcurrentMap<Pair<Int, String>, Int> = ConcurrentHashMap()
  private val segmentInterner: ConcurrentMap<String, String> = ConcurrentHashMap()

  private val nextId = AtomicInteger(1)

  // TODO It's possible to write this method without additional string allocations
  fun fromUrl(url: String): VirtualFileUrl {
    if (url.isEmpty()) return VirtualFileUrl(0)

    return VirtualFileUrl(
      url.split('/', '\\').fold(0) { parentId, segmentName -> getOrCreateId(parentId, segmentName) }
    )
  }

  private fun getOrCreateId(parent: Int, segmentName: String): Int {
    val internedSegmentName = segmentInterner.getOrPut(segmentName) { segmentName }
    return parent2id[parent to internedSegmentName] ?: {
      val newId = nextId.getAndIncrement()
      id2parent[newId] = parent
      id2segment[newId] = internedSegmentName
      parent2id[parent to internedSegmentName] = newId
      newId
    }()
  }

  fun fromVirtualFile(virtualFile: VirtualFile): VirtualFileUrl {
    // TODO: use segment names from virtualFiles?
    return fromUrl(virtualFile.url)
  }

  internal fun getUrlById(id: Int): String = if (id <= 0) "" else buildString {
    // capture maps into stack for performance
    buildUrlById(id, id2parent, id2segment)
  }

  private fun StringBuilder.buildUrlById(id: Int,
                                         hierarchyMap: ConcurrentMap<Int, Int>,
                                         segmentMap: ConcurrentMap<Int, String>): Boolean {
    // TODO rewrite with minimum amount of allocations, no recursion, no double lookups

    val segmentName = segmentMap.getValue(id)
    val parent = hierarchyMap.getValue(id)

    when {
      parent <= 0 -> {
        append(segmentName)
        return true
      }
      else -> {
        buildUrlById(parent, id2parent, id2segment)
        append("/")
        append(segmentName)
        return false
      }
    }
  }

  // TODO In the future it may be optimised by caching virtualFile in every trie node
  internal fun getVirtualFileById(id: Int): VirtualFile? = VirtualFileManager.getInstance().findFileByUrl(getUrlById(id))
/*
    TODO It may look like this + caching + invalidating
    val segmentName = getSegmentName(id).toString()

    val parent = id2parent.getValue(id)
    val parentParent = id2parent.getValue(parent)
    return if (parentParent <= 0) {
      val fileSystem = VirtualFileManager.getInstance().getFileSystem(getSegmentName(parent).toString())
      fileSystem?.findFileByPath(segmentName)
    } else {
      getVirtualFileById(parent)?.findChild(segmentName)
    }
  }
*/
}

// TODO Do we want to make it inline?
data class VirtualFileUrl(internal val id: Int)
{
  val url: String
    get() = VirtualFileUrlManager.getUrlById(id)

  val file: File?
    get() = filePath?.let { File(it) }

  val filePath: String?
    get() {
      val calculatedUrl = url
      val path = VfsUtilCore.urlToPath(calculatedUrl)
      if (path == calculatedUrl || path.isEmpty()) return null
      return path
    }

  val virtualFile
    get() = VirtualFileUrlManager.getVirtualFileById(id)

  // TODO: Rewrite
  fun isEqualOrParentOf(other: VirtualFileUrl): Boolean {
    val path = VfsUtilCore.urlToPath(other.url)
    val rootPath = VfsUtilCore.urlToPath(url)
    return FileUtil.isAncestor(rootPath, path, false)
  }

  //override fun equals(other: Any?): Boolean = id == (other as? VirtualFileUrl)?.id
  //override fun hashCode(): Int = id
  override fun toString(): String = url
}

// TODO It's possible to write it without additional string allocations besides absolutePath
fun File.toVirtualFileUrl(): VirtualFileUrl =
  VirtualFileUrlManager.fromUrl("file://${absolutePath.replace('\\', '/')}")

fun Path.toVirtualFileUrl(): VirtualFileUrl = toFile().toVirtualFileUrl()

val VirtualFile.virtualFileUrl
  get() = VirtualFileUrlManager.fromVirtualFile(this)
