// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.OrderRootsCache
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.ArrayUtil
import com.intellij.util.ConcurrencyUtil
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.VirtualFileUrlBridge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

class OrderRootsCacheBridge(val project: Project, parentDisposable: Disposable) : OrderRootsCache(parentDisposable) {
  private val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private val myRootUrls = AtomicReference<ConcurrentMap<CacheKey, Array<String>>>()
  private val myRootVirtualFiles = AtomicReference<ConcurrentMap<CacheKey, Array<VirtualFile>>>()

  override fun getOrComputeRoots(rootType: OrderRootType, flags: Int, computer: Supplier<out Collection<String>>): Array<VirtualFile> {
    cacheRootsIfNeeded(rootType, flags, computer)
    return myRootVirtualFiles.get()[CacheKey(rootType, flags)] ?: VirtualFile.EMPTY_ARRAY
  }

  override fun getOrComputeUrls(rootType: OrderRootType, flags: Int, computer: Supplier<out Collection<String>>): Array<String> {
    cacheRootsIfNeeded(rootType, flags, computer)
    return myRootUrls.get()[CacheKey(rootType, flags)] ?: ArrayUtil.EMPTY_STRING_ARRAY
  }

  override fun clearCache() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired()
    myRootUrls.set(null)
    myRootVirtualFiles.set(null)
  }

  private fun cacheRootsIfNeeded(rootType: OrderRootType, flags: Int, rootUrlsComputer: Supplier<out Collection<String>>) {
    val key = CacheKey(rootType, flags)
    var virtualFileUrls: List<VirtualFileUrlBridge>? = null
    if (myRootUrls.get()?.get(key) == null) {
      virtualFileUrls = rootUrlsComputer.get().map { virtualFileUrlManager.getOrCreateFromUri(it) as VirtualFileUrlBridge }
      ConcurrencyUtil.cacheOrGet(myRootUrls, ConcurrentHashMap()).computeIfAbsent(key) { ArrayUtil.toStringArray(virtualFileUrls!!.map { it.url }) }
    }
    if (myRootVirtualFiles.get()?.get(key) == null) {
      if (virtualFileUrls == null) {
        virtualFileUrls = rootUrlsComputer.get().map { virtualFileUrlManager.getOrCreateFromUri(it) as VirtualFileUrlBridge }
      }
      ConcurrencyUtil.cacheOrGet(myRootVirtualFiles, ConcurrentHashMap()).computeIfAbsent(key) {
        VfsUtilCore.toVirtualFileArray(virtualFileUrls.mapNotNull { it.file })
      }
    }
  }
}