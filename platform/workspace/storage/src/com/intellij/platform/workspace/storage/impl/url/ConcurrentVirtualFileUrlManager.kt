// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.url

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
public open class ConcurrentVirtualFileUrlManager : VirtualFileUrlManager {

  internal val root = NewVirtualFileUrlImpl("/", this)

  // The empty URL is one shared instance. It is not part of the trie, and findByUrl never finds it.
  // getOrCreateFromUrl gives the empty URL to callers. Therefore, createVirtualFileUrl must make it, and then all nodes
  // have the same class. The initialization is lazy because a subclass can override createVirtualFileUrl.
  private val emptyUrl: NewVirtualFileUrlImpl by lazy { createVirtualFileUrl("", this, null) as NewVirtualFileUrlImpl }

  private val name2Canonical = ConcurrentHashMap<String, String>()

  override fun getOrCreateFromUrl(uri: String): VirtualFileUrl {
    if (uri.isEmpty()) return emptyUrl
    return insertPath(uri)
  }

  override fun findByUrl(uri: String): VirtualFileUrl? {
    val node = findNode(uri) ?: return null
    // Return the node only if this exact URL was registered via getOrCreateFromUrl
    return if (node.isRegistered()) node else null
  }

  private fun findNode(url: String): NewVirtualFileUrlImpl? {
    var currentNode: NewVirtualFileUrlImpl = root
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

  override fun fromPath(path: String): VirtualFileUrl {
    val url = URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtil.toSystemIndependentName(path)
    return getOrCreateFromUrl(url)
  }

  /**
   * Returns class of instances produced by [createVirtualFileUrl], it's used during serialization.
   */
  public open val virtualFileUrlImplementationClass: Class<out VirtualFileUrl>
    get() = NewVirtualFileUrlImpl::class.java

  /**
   * [parent] is `null` only for a node that is not part of the trie. The empty URL is the only such node.
   */
  public open fun createVirtualFileUrl(name: String, manager: ConcurrentVirtualFileUrlManager, parent: VirtualFileUrl?): VirtualFileUrl {
    return NewVirtualFileUrlImpl(name, manager, parent as NewVirtualFileUrlImpl?)
  }

  internal fun append(relativePath: String, parent: NewVirtualFileUrlImpl): VirtualFileUrl {
    return insertPath(relativePath, parent)
  }

  /**
   * Returns the single instance of [name] shared by every trie node with that segment name,
   * so a segment occurring under many parents (e.g. "src") is stored only once instead of once per node.
   * Populated lazily as new nodes are created; [findByUrl] never calls it, so lookups don't pollute it.
   */
  internal fun canonicalizeName(name: String): String = name2Canonical.computeIfAbsent(name) { it }

  private fun insertPath(url: String, node: NewVirtualFileUrlImpl = root): VirtualFileUrl {
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
