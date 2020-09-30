// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.VirtualFileUrlManager

/**
 * This method was extracted from [VirtualFileUrlManager] because of dependency management. Storage
 * should have as many dependencies as possible and there is no dependency to intellij.platform.core module.
 * That's why this method was declared here, where service was registered.
 */
fun VirtualFileUrlManager.Companion.getInstance(project: Project) = project.service<VirtualFileUrlManager>()