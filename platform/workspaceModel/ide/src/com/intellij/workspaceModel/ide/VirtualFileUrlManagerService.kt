// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.workspaceModel.storage.vfu.VirtualFileUrl
import com.intellij.workspaceModel.storage.vfu.VirtualFileUrlManager
import org.jetbrains.annotations.TestOnly

/**
 * This method was extracted from [VirtualFileUrlManager] because of dependency management. Storage
 * should have as many dependencies as possible and there is no dependency to intellij.platform.core module.
 * That's why this method was declared here, where service was registered.
 */
fun VirtualFileUrlManager.Companion.getInstance(project: Project) = project.service<VirtualFileUrlManager>()

class VirtualFileUrlManagerImpl(private val project: Project) : VirtualFileUrlManager {
  private var testDisposable: Disposable? = null
  private var projectDisposable = Disposer.newDisposable(project, "VirtualFileUrlManager")
  private val filePointerManager = VirtualFilePointerManager.getInstance()
  private val rootsValidityChangedListener
    get() = ProjectRootManagerImpl.getInstanceImpl(project).rootsValidityChangedListener

  override fun fromUrl(url: String): VirtualFileUrl {
    return filePointerManager.create(url, testDisposable ?: projectDisposable, rootsValidityChangedListener) as VirtualFileUrl
  }

  fun fromDirUrl(url: String): VirtualFileUrl {
    return filePointerManager.createDirectoryPointer(url, true, testDisposable ?: projectDisposable, rootsValidityChangedListener) as VirtualFileUrl
  }

  override fun fromPath(path: String): VirtualFileUrl {
    val systemIndependentName = FileUtil.toSystemIndependentName(path)
    return fromUrl("${if (systemIndependentName.endsWith(".jar")) "jar://" else "file://"}$systemIndependentName")
  }

  override fun getParentVirtualUrl(vfu: VirtualFileUrl): VirtualFileUrl? {
    val fileUrl = vfu.url
    val index = fileUrl.lastIndexOf('/')
    return if (index >= 0) fromUrl(fileUrl.substring(0, index)) else null
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

fun VirtualFileUrl.append(manager: VirtualFileUrlManager, relativePath: String): VirtualFileUrl {
  return manager.fromUrl(this.url + "/" + relativePath.removePrefix("/"))
}

fun VirtualFileUrl.isEqualOrParentOf(other: VirtualFileUrl): Boolean {
  val url = url
  val otherUrl = other.url
  return otherUrl.startsWith(url)
}