// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.getOutsiderFileOrigin
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
interface KtModuleFactory {
    companion object {
        val EP_NAME: ExtensionPointName<KtModuleFactory> =
            ExtensionPointName.create("org.jetbrains.kotlin.ktModuleFactory")
    }

    fun createModule(moduleInfo: ModuleInfo): KtModule?

    fun createModuleWithNonOriginalFile(
        moduleInfo: ModuleInfo,
        fakeFile: VirtualFile,
        originalFile: VirtualFile,
    ): KtModule? = createModule(moduleInfo)
}

@ApiStatus.Internal
fun IdeaModuleInfo.toKtModule(): KtModule {
    return ProjectStructureProviderIdeImpl.getInstance(project).getKtModuleByModuleInfo(this)
}

@ApiStatus.Internal
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
inline fun <reified T : KtModule> IdeaModuleInfo.toKtModuleOfType(): @kotlin.internal.NoInfer T {
    return toKtModule() as T
}

internal class ProjectStructureProviderIdeImpl(private val project: Project) : ProjectStructureProvider() {
    override fun getKtModuleForKtElement(element: PsiElement): KtModule {
        val config = ModuleInfoProvider.Configuration(createSourceLibraryInfoForLibraryBinaries = false)
        val moduleInfo = ModuleInfoProvider.getInstance(project).firstOrNull(element, config)
            ?: NotUnderContentRootModuleInfo(project, element.containingFile as? KtFile)

        val virtualFile = element.containingFile?.virtualFile
        val originalFile = virtualFile?.let { getOutsiderFileOrigin(project, it) }
        if (originalFile != null) {
            forEachModuleFactory {
                createModuleWithNonOriginalFile(
                    moduleInfo = moduleInfo,
                    fakeFile = virtualFile,
                    originalFile = originalFile,
                )?.let { return it }
            }
        }

        return getKtModuleByModuleInfo(moduleInfo)
    }

    // TODO maybe introduce some cache?
    fun getKtModuleByModuleInfo(moduleInfo: ModuleInfo): KtModule =
        createKtModuleByModuleInfo(moduleInfo)

    private fun createKtModuleByModuleInfo(moduleInfo: ModuleInfo): KtModule {
        forEachModuleFactory {
            createModule(moduleInfo)?.let { return it }
        }

        return when (moduleInfo) {
            is ModuleSourceInfo -> KtSourceModuleByModuleInfo(moduleInfo)
            is LibraryInfo -> KtLibraryModuleByModuleInfo(moduleInfo)
            is SdkInfo -> SdkKtModuleByModuleInfo(moduleInfo)
            is LibrarySourceInfo -> KtLibrarySourceModuleByModuleInfo(moduleInfo)
            is NotUnderContentRootModuleInfo -> NotUnderContentRootModuleByModuleInfo(moduleInfo)
            else -> NotUnderContentRootModuleByModuleInfo(moduleInfo as IdeaModuleInfo)
        }
    }

    companion object {
        fun getInstance(project: Project): ProjectStructureProviderIdeImpl {
            return ProjectStructureProvider.getInstance(project) as ProjectStructureProviderIdeImpl
        }
    }
}

private inline fun forEachModuleFactory(action: KtModuleFactory.() -> Unit) {
    for (extension in KtModuleFactory.EP_NAME.extensions) {
        extension.action()
    }
}
