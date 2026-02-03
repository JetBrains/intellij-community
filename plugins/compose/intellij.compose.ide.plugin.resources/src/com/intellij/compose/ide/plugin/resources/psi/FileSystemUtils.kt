// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import java.nio.file.Path

internal fun Path.toPsiFile(project: Project): PsiFile? = toVirtualFile()?.toPsiFile(project)

internal fun Path.toVirtualFile(): VirtualFile? = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(this)