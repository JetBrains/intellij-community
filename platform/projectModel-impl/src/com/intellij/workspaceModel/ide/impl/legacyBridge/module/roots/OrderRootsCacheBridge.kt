// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.OrderRootsCache
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.util.ArrayUtil
import com.intellij.util.ConcurrencyUtil
import com.intellij.workspaceModel.ide.impl.VirtualFileUrlBridge
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

@ApiStatus.Internal
class OrderRootsCacheBridge(project: Project, parentDisposable: Disposable) : OrderRootsCache(parentDisposable) {
  class UrlsAndVirtualFiles(val urls: Array<String>, val virtualFiles: Array<VirtualFile>)
  private val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
  private val myRootUrlsAndFiles = AtomicReference<ConcurrentMap<CacheKey, UrlsAndVirtualFiles>>()

  override fun getOrComputeRoots(rootType: OrderRootType, flags: Int, computer: Supplier<out Collection<String>>): Array<VirtualFile> {
    val urlsAndFiles = ensureCached(rootType, flags, computer)
    return urlsAndFiles.virtualFiles
  }

  override fun getOrComputeUrls(rootType: OrderRootType, flags: Int, computer: Supplier<out Collection<String>>): Array<String> {
    val urlsAndFiles = ensureCached(rootType, flags, computer)
    return urlsAndFiles.urls
  }

  override fun clearCache() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired()
    myRootUrlsAndFiles.set(null)
  }

  private fun ensureCached(rootType: OrderRootType, flags: Int, rootUrlsComputer: Supplier<out Collection<String>>): UrlsAndVirtualFiles {
    val key = CacheKey(rootType, flags)
    val entry = myRootUrlsAndFiles.get()?.get(key)
    if (entry != null) return entry
    val virtualFileUrls = rootUrlsComputer.get().map { virtualFileUrlManager.getOrCreateFromUrl(it) as VirtualFileUrlBridge }
    val urls = ArrayUtil.toStringArray(virtualFileUrls.map { it.url })
    val virtualFiles = VfsUtilCore.toVirtualFileArray(virtualFileUrls.mapNotNull { it.file })
    return ConcurrencyUtil.cacheOrGet(myRootUrlsAndFiles, ConcurrentHashMap()).computeIfAbsent(key) { UrlsAndVirtualFiles(urls, virtualFiles) }
  }
}