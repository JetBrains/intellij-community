// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.VirtualFileUrlManager

fun VirtualFileUrlManager.Companion.getInstance(project: Project) = project.service<VirtualFileUrlManager>()