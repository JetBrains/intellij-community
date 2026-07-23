// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.url

import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.containers.TreeNodeProcessingResult
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
public open class NewVirtualFileUrlImpl(
  private val nodeName: String,
  private val manager: ConcurrentVirtualFileUrlManager,
  internal val parent: NewVirtualFileUrlImpl? = null,
) : VirtualFileUrl {
  // Lazily allocated so that leaf nodes never allocate a children map
  private val childrenRef = AtomicReference<ConcurrentHashMap<String, NewVirtualFileUrlImpl>?>()

  private var url: String? = null

  // Set when this node is the terminal of getOrCreateFromUrl/append, i.e. an explicitly registered URL
  @Volatile
  private var registered = false

  internal fun markRegistered() {
    if (!registered) registered = true
  }

  internal fun isRegistered(): Boolean = registered

  /** Returns the child for [nameSegment], or `null` if this node has no children yet or has no such child. */
  internal fun findChild(nameSegment: String): NewVirtualFileUrlImpl? = childrenRef.get()?.get(nameSegment)

  /**
   * Returns the existing child for [nameSegment] or atomically creates it,
   * allocating this node's children map on first use.
   */
  internal fun getOrCreateChild(nameSegment: String): NewVirtualFileUrlImpl {
    childrenRef.get()?.get(nameSegment)?.let { return it }
    val canonicalName = manager.canonicalizeName(nameSegment)
    return getOrCreateChildren().computeIfAbsent(canonicalName) {
      manager.createVirtualFileUrl(it, manager, this) as NewVirtualFileUrlImpl
    }
  }

  private fun getOrCreateChildren(): ConcurrentHashMap<String, NewVirtualFileUrlImpl> {
    val myChildrenMap = childrenRef.get()
    return if (myChildrenMap == null) {
      val created = ConcurrentHashMap<String, NewVirtualFileUrlImpl>()
      if (childrenRef.compareAndSet(null, created)) created else childrenRef.get()!!
    }
    else {
      myChildrenMap
    }
  }

  override fun getUrl(): String {
    var u = url
    if (u == null) {
      u = computeUrlInternal()
      url = u
    }
    return u
  }

  override fun getFileName(): String {
    return nodeName
  }

  override fun getParent(): VirtualFileUrl? {
    val parentNode = parent ?: return null
    if (parentNode === manager.root) return null
    return parentNode
  }

  override fun getPresentableUrl(): String {
    val calculatedUrl = this.getUrl()
    if (calculatedUrl.startsWith("file://")) {
      return calculatedUrl.substring("file://".length)
    }
    else if (calculatedUrl.startsWith("jar://")) {
      val removedSuffix = calculatedUrl.removeSuffix("!/").removeSuffix("!")
      return removedSuffix.substring("jar://".length)
    }
    return calculatedUrl
  }

  override fun getSubTreeFileUrls(): List<VirtualFileUrl> {
    childrenRef.get() ?: return emptyList()
    val result = ArrayList<VirtualFileUrl>()
    computeSubtree(result)
    return result
  }

  override fun append(relativePath: String): VirtualFileUrl {
    return manager.append(relativePath.removePrefix("/"), this)
  }

  private fun computeUrlInternal(): String {
    if (parent == null) {
      return nodeName
    }
    if (parent === manager.root && nodeName.isEmpty()) {
      return "/"
    }
    val builder = StringBuilder()
    buildPathRecursive(builder)
    return builder.toString()
  }

  private fun buildPathRecursive(builder: StringBuilder) {
    val parentNode = parent ?: return
    parentNode.buildPathRecursive(builder)
    // A separator is needed only when there is a preceding segment, i.e. when the parent is itself a real node (not the root).
    if (parentNode.parent != null) {
      builder.append('/')
    }
    builder.append(nodeName)
  }

  private fun computeSubtree(result: MutableList<VirtualFileUrl>) {
    val children = childrenRef.get() ?: return
    children.values.forEach {
      result.add(it)
      it.computeSubtree(result)
    }
  }

  internal fun processChildrenRecursively(processor: (NewVirtualFileUrlImpl) -> TreeNodeProcessingResult): Boolean {
    val children = childrenRef.get() ?: return true
    for (child in children.values) {
      when (processor(child)) {
        TreeNodeProcessingResult.CONTINUE -> {
          if (!child.processChildrenRecursively(processor)) {
            return false
          }
        }
        TreeNodeProcessingResult.SKIP_CHILDREN -> {}
        TreeNodeProcessingResult.SKIP_TO_PARENT -> return true
        TreeNodeProcessingResult.STOP -> return false
      }
    }
    return true
  }
}
