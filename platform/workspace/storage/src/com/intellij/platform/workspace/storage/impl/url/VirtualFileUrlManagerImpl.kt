// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.url

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * A lock-free trie of [VirtualFileUrlImpl] nodes. Every node is a [VirtualFileUrl], and the nodes share the path prefixes.
 */
@ApiStatus.Internal
public open class VirtualFileUrlManagerImpl : VirtualFileUrlManager {

  internal val root = VirtualFileUrlImpl("/", this)

  // The empty URL is one shared instance. It is not part of the trie, and get never finds it.
  // storeAndGet gives the empty URL to callers. Therefore, createVirtualFileUrl must make it, and then all nodes
  // have the same class. The initialization is lazy because a subclass can override createVirtualFileUrl.
  private val emptyUrl: VirtualFileUrlImpl by lazy { createVirtualFileUrl("", this, null) as VirtualFileUrlImpl }

  private val name2Canonical = ConcurrentHashMap<String, String>()

  override fun storeAndGet(url: String): VirtualFileUrl {
    if (url.isEmpty()) return emptyUrl
    return insertPath(url)
  }

  /**
   * Returns the URL whose '/'-separated segments are [segments].
   * The result is the same as `storeAndGet(segments.joinToString("/"))`, but this method does not build the string.
   * The cache deserializer reads the segments from the cache and calls this method.
   */
  public fun fromUrlSegments(segments: List<String>): VirtualFileUrl {
    // "".split('/') is [""], and storeAndGet("") returns the empty URL
    if (segments.isEmpty() || (segments.size == 1 && segments[0].isEmpty())) return emptyUrl
    var curr = root
    for (segment in segments) {
      curr = curr.getOrCreateChild(segment)
    }
    curr.markRegistered()
    return curr
  }

  override fun get(url: String): VirtualFileUrl? {
    val node = findNode(url) ?: return null
    // Return the node only if this exact URL was registered via storeAndGet
    return if (node.isRegistered()) node else null
  }

  private fun findNode(url: String): VirtualFileUrlImpl? {
    var currentNode: VirtualFileUrlImpl = root
    forEachNameSegment(url) { nameSegment ->
      currentNode = currentNode.findChild(nameSegment) ?: return null
    }
    return currentNode
  }

  /**
   * Processes children of [url] and their children recursively using [processor]. [url] itself isn't processed.
   * @return `true` if processing finished normally, or `false` if [processor] returned [STOP][TreeNodeProcessingResult.STOP].
   */
  public fun processChildrenRecursively(url: String, processor: (VirtualFileUrl) -> TreeNodeProcessingResult): Boolean {
    val node = findNode(url) ?: return true
    return node.processChildrenRecursively { childNode ->
      if (childNode.isRegistered()) processor(childNode) else TreeNodeProcessingResult.CONTINUE
    }
  }

  /**
   * Returns [VirtualFileUrl] instances which were already created by this manager, without creating new ones.
   * Every trie node is itself a [VirtualFileUrl], so this returns the nodes which were explicitly requested
   * via [storeAndGet] or [append], skipping the intermediate ones created along the way.
   */
  public fun getCachedVirtualFileUrls(): List<VirtualFileUrl> {
    val result = ArrayList<VirtualFileUrl>()
    root.processChildrenRecursively { childNode ->
      if (childNode.isRegistered()) result.add(childNode)
      TreeNodeProcessingResult.CONTINUE
    }
    return result
  }

  override fun fromPath(path: String): VirtualFileUrl {
    val url = URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtil.toSystemIndependentName(path)
    return storeAndGet(url)
  }

  /**
   * Returns class of instances produced by [createVirtualFileUrl], it's used during serialization.
   */
  public open val virtualFileUrlImplementationClass: Class<out VirtualFileUrl>
    get() = VirtualFileUrlImpl::class.java

  /**
   * [parent] is `null` only for a node that is not part of the trie. The empty URL is the only such node.
   */
  public open fun createVirtualFileUrl(name: String, manager: VirtualFileUrlManagerImpl, parent: VirtualFileUrl?): VirtualFileUrl {
    return VirtualFileUrlImpl(name, manager, parent as VirtualFileUrlImpl?)
  }

  internal fun append(relativePath: String, parent: VirtualFileUrlImpl): VirtualFileUrl {
    return insertPath(relativePath, parent)
  }

  /**
   * Returns the single instance of [name] shared by every trie node with that segment name,
   * so a segment occurring under many parents (e.g. "src") is stored only once instead of once per node.
   * Populated lazily as new nodes are created; [get] never calls it, so lookups don't pollute it.
   */
  internal fun canonicalizeName(name: String): String = name2Canonical.computeIfAbsent(name) { it }

  private fun insertPath(url: String, node: VirtualFileUrlImpl = root): VirtualFileUrl {
    var curr = node
    forEachNameSegment(url) { nameSegment ->
      curr = curr.getOrCreateChild(nameSegment)
    }
    curr.markRegistered()
    return curr
  }

  /**
   * Invokes [action] for each '/'  or '\' separated segment of [path] without allocating an intermediate list
   */
  private inline fun forEachNameSegment(path: String, action: (String) -> Unit) {
    var start = 0
    var i = 0
    while (i < path.length) {
      val c = path[i]
      if (c == '/' || c == '\\') {
        action(path.substring(start, i))
        start = i + 1
      }
      i++
    }
    action(path.substring(start))
  }
}
