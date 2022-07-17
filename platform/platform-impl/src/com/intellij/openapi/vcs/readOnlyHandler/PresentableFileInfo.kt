// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.readOnlyHandler

import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class PresentableFileInfo(vf: VirtualFile, val presentation: TargetPresentation, project: Project) : FileInfo(vf, project)