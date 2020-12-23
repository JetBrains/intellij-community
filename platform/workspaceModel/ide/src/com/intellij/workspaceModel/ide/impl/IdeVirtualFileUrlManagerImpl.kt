// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.toPath
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.TestOnly

class IdeVirtualFileUrlManagerImpl(private val project: Project) : VirtualFileUrlManager {
  private var testDisposable: Disposable? = null
  private var projectDisposable = Disposer.newDisposable(project, "VirtualFileUrlManager")
  private val filePointerManager = VirtualFilePointerManager.getInstance()
  private val rootsValidityChangedListener
    get() = ProjectRootManagerImpl.getInstanceImpl(project).rootsValidityChangedListener
  private val virtualFilePointerListener = object: VirtualFilePointerListener {
    override fun beforeValidityChanged(pointers: Array<VirtualFilePointer>) {
      if (project.isDisposed) return
      if (!isValidRootsChangedForWorkspaceModel(pointers)) return;
      rootsValidityChangedListener.beforeValidityChanged(pointers)
    }

    override fun validityChanged(pointers: Array<VirtualFilePointer>) {
      if (project.isDisposed) return
      if (!isValidRootsChangedForWorkspaceModel(pointers)) return;
      rootsValidityChangedListener.validityChanged(pointers)
    }

    private fun isValidRootsChangedForWorkspaceModel(pointers: Array<VirtualFilePointer>): Boolean {
      val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
      val virtualFileUrlIndex = entityStorage.getVirtualFileUrlIndex()
      for (pointer in pointers) {
        if (virtualFileUrlIndex.findEntitiesByUrl((pointer as VirtualFileUrl)).iterator().hasNext()) return true
      }
      return false
    }
  }

  override fun fromUrl(url: String): VirtualFileUrl {
    return filePointerManager.create(url, testDisposable ?: projectDisposable, virtualFilePointerListener) as VirtualFileUrl
  }

  fun fromDirUrl(url: String): VirtualFileUrl {
    return filePointerManager.createDirectoryPointer(url, true, testDisposable ?: projectDisposable, virtualFilePointerListener) as VirtualFileUrl
  }

  override fun fromPath(path: String): VirtualFileUrl {
    val systemIndependentName = FileUtil.toSystemIndependentName(path)
    return fromUrl("file://$systemIndependentName")
  }

  override fun getParentVirtualUrl(vfu: VirtualFileUrl): VirtualFileUrl? {
    val url = vfu.url
    val parent = vfu.toPath().parent ?: return null
    val protocolEnd = url.indexOf(URLUtil.SCHEME_SEPARATOR)
    if (protocolEnd == -1) return null
    val protocol = url.substring(0, protocolEnd)
    val systemIndependentName = FileUtil.toSystemIndependentName(parent.toString())
    return fromUrl("$protocol${URLUtil.SCHEME_SEPARATOR}$systemIndependentName")
  }

  @TestOnly
  fun startTrackPointersCreatedInTest() {
    testDisposable = Disposer.newDisposable("VirtualFileUrlManager")
  }

  @TestOnly
  fun disposePointersCreatedInTest() {
    if (testDisposable != null) {
      Disposer.dispose(testDisposable!!)
      testDisposable = null
    }
  }
}