// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.kotlin.idea.base.util.module as newModule

fun VirtualFile.getSourceRoot(project: Project): VirtualFile? = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(this)

val PsiFileSystemItem.sourceRoot: VirtualFile?
    get() = virtualFile?.getSourceRoot(project)

@Deprecated(
    "Use 'com.intellij.openapi.project.rootManager' instead.",
    ReplaceWith("rootManager", imports = ["com.intellij.openapi.project.rootManager"])
)
val Module.rootManager: ModuleRootManager
    get() = ModuleRootManager.getInstance(this)

val Module.sourceRoots: Array<VirtualFile>
    get() {
        @Suppress("DEPRECATION")
        return rootManager.sourceRoots
    }

@Deprecated(
    "Use org.jetbrains.kotlin.idea.base.util.module instead.",
    ReplaceWith("module", "org.jetbrains.kotlin.idea.base.util.module")
)
val PsiElement.module: Module?
    get() = newModule

@Deprecated(
    "Use 'ModuleUtilCore.findModuleForFile()' instead.",
    ReplaceWith("ModuleUtilCore.findModuleForFile(this, project)", "com.intellij.openapi.module.ModuleUtilCore")
)
fun VirtualFile.findModule(project: Project) = ModuleUtilCore.findModuleForFile(this, project)

fun Module.createPointer() =
    ModulePointerManager.getInstance(project).create(this)