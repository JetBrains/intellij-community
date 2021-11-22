// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

class VirtualFileUrlsLazyInitializer: StartupActivity.Background {
  override fun runActivity(project: Project) {
    (VirtualFileUrlManager.getInstance(project) as? IdeVirtualFileUrlManagerImpl)?.getCachedVirtualFileUrls()
      ?.forEach { virtualFileUrl -> (virtualFileUrl as? VirtualFileUrlBridge)?.isValid }
  }
}