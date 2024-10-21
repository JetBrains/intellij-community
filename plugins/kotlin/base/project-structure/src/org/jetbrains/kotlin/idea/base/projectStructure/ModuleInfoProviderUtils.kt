// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleInfoProviderUtils")
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider.Result.create
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider.Configuration
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.psi.KtFile

@K1ModeProjectStructureApi
val PsiElement.moduleInfo: IdeaModuleInfo
    get() = moduleInfoOrNull ?: NotUnderContentRootModuleInfo(project, containingFile as? KtFile)

@K1ModeProjectStructureApi
val PsiElement.moduleInfoOrNull: IdeaModuleInfo?
    get() {
        val anchorElement = ModuleInfoProvider.findAnchorElement(this)
        return if (anchorElement != null) {
            cachedModuleInfo(anchorElement)
        } else {
            ModuleInfoProvider.getInstance(project).firstOrNull(this)
        }
    }

private fun cachedModuleInfo(
  anchorElement: PsiElement,
): IdeaModuleInfo? = CachedValuesManager.getCachedValue<IdeaModuleInfo?>(anchorElement) {
    val project = anchorElement.project
    create(
      ModuleInfoProvider.getInstance(project).firstOrNull(anchorElement),
      ProjectRootModificationTracker.getInstance(project),
      JavaLibraryModificationTracker.getInstance(project),
      KotlinModificationTrackerFactory.getInstance(project).createProjectWideOutOfBlockModificationTracker(),
    )
}

@K1ModeProjectStructureApi
fun ModuleInfoProvider.firstOrNull(element: PsiElement, config: Configuration = Configuration.Default): IdeaModuleInfo? =
    collect(element, config).unwrap(ModuleInfoProvider.LOG::warn).firstOrNull()

@K1ModeProjectStructureApi
fun ModuleInfoProvider.firstOrNull(virtualFile: VirtualFile): IdeaModuleInfo? =
    collect(virtualFile).unwrap(ModuleInfoProvider.LOG::warn).firstOrNull()

@K1ModeProjectStructureApi
fun ModuleInfoProvider.collectLibraryBinariesModuleInfos(virtualFile: VirtualFile): Sequence<BinaryModuleInfo> {
    return collectOfType<BinaryModuleInfo>(virtualFile)
}

@K1ModeProjectStructureApi
fun ModuleInfoProvider.collectLibrarySourcesModuleInfos(virtualFile: VirtualFile): Sequence<LibrarySourceInfo> {
    return collectOfType<LibrarySourceInfo>(virtualFile)
}

@K1ModeProjectStructureApi
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