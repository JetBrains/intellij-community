// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.ide.toPath
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

/**
 * This manager introduced as a temporary solution for Rider and <b>should not be used in any other places.</b>
 * The aim of this manager is to return [com.intellij.openapi.vfs.impl.LightFilePointerUrl] to avoid pointers
 * mutability. [VirtualFilePointerManager] returns it if the passed path doesn't contain schema. This manager
 * will be removed after creating [VirtualFileUrl] which also implements [VirtualFilePointer]
 */
class LightIdeVirtualFileUrlManagerImpl(project: Project) : IdeVirtualFileUrlManagerImpl(project) {
  override fun fromPath(path: String): VirtualFileUrl {
    return fromUrl(FileUtil.toSystemIndependentName(path))
  }

  override fun getParentVirtualUrl(vfu: VirtualFileUrl): VirtualFileUrl? {
    val url = vfu.url
    val parent = vfu.toPath().parent ?: return null
    val protocolEnd = url.indexOf(URLUtil.SCHEME_SEPARATOR)
    if (protocolEnd == -1) {
      val systemIndependentName = FileUtil.toSystemIndependentName(parent.toString())
      return fromUrl(systemIndependentName)
    } else {
      val protocol = url.substring(0, protocolEnd)
      val systemIndependentName = FileUtil.toSystemIndependentName(parent.toString())
      return fromUrl("$protocol${URLUtil.SCHEME_SEPARATOR}$systemIndependentName")
    }
  }
}