// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import java.io.File

@Deprecated(
    "Use 'modules.asList()' instead.",
    ReplaceWith("this.modules.asList()", "com.intellij.openapi.project.modules")
)
fun Project.allModules(): List<Module> = modules.asList()

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.util.sdk' instead",
    ReplaceWith("this.sdk", "org.jetbrains.kotlin.idea.base.util.sdk"),
)
val Module.sdk: Sdk? get() = ModuleRootManager.getInstance(this).sdk

@Deprecated(
    "Use JavaSdk.getInstance().getVersion() instead",
    ReplaceWith("JavaSdk.getInstance().getVersion(this)", "com.intellij.openapi.projectRoots.JavaSdk")
)
val Sdk.version: JavaSdkVersion? get() = JavaSdk.getInstance().getVersion(this)

fun Module.getModuleDir(): String = File(moduleFilePath).parent!!.replace(File.separatorChar, '/')

fun Library.ModifiableModel.replaceFileRoot(oldFile: File, newFile: File) {
    val oldRoot = VfsUtil.getUrlForLibraryRoot(oldFile)
    val newRoot = VfsUtil.getUrlForLibraryRoot(newFile)

    fun replaceInRootType(rootType: OrderRootType) {
        for (url in getUrls(rootType)) {
            if (oldRoot == url) {
                removeRoot(url, rootType)
                addRoot(newRoot, rootType)
            }
        }
    }

    replaceInRootType(OrderRootType.CLASSES)
    replaceInRootType(OrderRootType.SOURCES)
}

fun VirtualFile.getModule(project: Project) = ModuleUtilCore.findModuleForFile(this, project)

val PsiElement.module
    get() = ModuleUtilCore.findModuleForPsiElement(this)