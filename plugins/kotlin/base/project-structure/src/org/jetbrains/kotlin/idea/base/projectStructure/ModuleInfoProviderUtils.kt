// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleInfoProviderUtils")
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider.Configuration
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.psi.KtFile

val PsiElement.moduleInfo: IdeaModuleInfo
    get() = ModuleInfoProvider.getInstance(project).firstOrNull(this) ?: NotUnderContentRootModuleInfo(project, containingFile as? KtFile)

val PsiElement.moduleInfoOrNull: IdeaModuleInfo?
    get() = ModuleInfoProvider.getInstance(project).firstOrNull(this)

fun ModuleInfoProvider.firstOrNull(element: PsiElement, config: Configuration = Configuration.Default): IdeaModuleInfo? =
    collect(element, config).unwrap(ModuleInfoProvider.LOG::warn).firstOrNull()

fun ModuleInfoProvider.firstOrNull(virtualFile: VirtualFile): IdeaModuleInfo? =
    collect(virtualFile).unwrap(ModuleInfoProvider.LOG::warn).firstOrNull()

fun ModuleInfoProvider.collectLibraryBinariesModuleInfos(virtualFile: VirtualFile): Sequence<BinaryModuleInfo> {
    return collectOfType<BinaryModuleInfo>(virtualFile)
}

fun ModuleInfoProvider.collectLibrarySourcesModuleInfos(virtualFile: VirtualFile): Sequence<LibrarySourceInfo> {
    return collectOfType<LibrarySourceInfo>(virtualFile)
}

fun ModuleInfo.unwrapModuleSourceInfo(): ModuleSourceInfo? {
    return when (this) {
        is ModuleSourceInfo -> this
        is PlatformModuleInfo -> this.platformModule
        else -> null
    }
}

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
private inline fun <reified T : IdeaModuleInfo> ModuleInfoProvider.collectOfType(file: VirtualFile): Sequence<@kotlin.internal.NoInfer T> =
    collect(file, isLibrarySource = false).unwrap(ModuleInfoProvider.LOG::warn).filterIsInstance<T>()