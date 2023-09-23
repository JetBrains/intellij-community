// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptDependencyModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analysis.project.structure.impl.KtCodeFragmentModuleImpl
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.ProjectStructureProviderIdeImpl.Companion.getKtModuleByModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.getOutsiderFileOrigin
import org.jetbrains.kotlin.idea.base.util.isOutsiderFile
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
interface KtModuleFactory {
    companion object {
        val EP_NAME: ExtensionPointName<KtModuleFactory> =
            ExtensionPointName.create("org.jetbrains.kotlin.ktModuleFactory")
    }

    fun createModule(moduleInfo: ModuleInfo): KtModule?
}

@ApiStatus.Internal
fun IdeaModuleInfo.toKtModule(): KtModule = ProjectStructureProviderIdeImpl.getKtModuleByModuleInfo(this)

@ApiStatus.Internal
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
inline fun <reified T : KtModule> IdeaModuleInfo.toKtModuleOfType(): @kotlin.internal.NoInfer T {
    return toKtModule() as T
}

internal class ProjectStructureProviderIdeImpl(private val project: Project) : ProjectStructureProvider() {
    override fun getModule(element: PsiElement, contextualModule: KtModule?): KtModule {
        if (contextualModule is KtSourceModuleByModuleInfoForOutsider || contextualModule is KtScriptDependencyModule) {
            val virtualFile = element.containingFile?.virtualFile
            if (virtualFile != null && virtualFile in contextualModule.contentScope) {
                return contextualModule
            }
        }

        val anchorElement = ModuleInfoProvider.findAnchorElement(element)
        return if (anchorElement != null) {
            cachedKtModule(anchorElement)
        } else {
            calculateKtModule(element)
        }
    }

    companion object {
        // TODO maybe introduce some cache?
        fun getKtModuleByModuleInfo(moduleInfo: ModuleInfo): KtModule = createKtModuleByModuleInfo(moduleInfo)
    }
}

private fun cachedKtModule(anchorElement: PsiElement): KtModule = CachedValuesManager.getCachedValue<KtModule>(anchorElement) {
    val project = anchorElement.project
    CachedValueProvider.Result.create(
        calculateKtModule(anchorElement),
        ProjectRootModificationTracker.getInstance(project),
        JavaLibraryModificationTracker.getInstance(project),
        KotlinModificationTrackerFactory.getInstance(project).createProjectWideOutOfBlockModificationTracker(),
    )
}

private inline fun forEachModuleFactory(action: KtModuleFactory.() -> Unit) {
    for (extension in KtModuleFactory.EP_NAME.extensions) {
        extension.action()
    }
}

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

private fun calculateKtModule(psiElement: PsiElement): KtModule {
    val containingFile = psiElement.containingFile
    val virtualFile = containingFile?.virtualFile
    val project = psiElement.project
    val config = ModuleInfoProvider.Configuration(
        createSourceLibraryInfoForLibraryBinaries = false,
        preferModulesFromExtensions = virtualFile?.nameSequence?.endsWith(".kts") == true
    )

    val moduleInfo = ModuleInfoProvider.getInstance(project).firstOrNull(psiElement, config)
        ?: NotUnderContentRootModuleInfo(project, psiElement.containingFile as? KtFile)

    if (virtualFile != null && isOutsiderFile(virtualFile) && moduleInfo is ModuleSourceInfo) {
        val originalFile = getOutsiderFileOrigin(project, virtualFile)
        return KtSourceModuleByModuleInfoForOutsider(virtualFile, originalFile, moduleInfo)
    }

    val moduleByModuleInfo = getKtModuleByModuleInfo(moduleInfo)

    if (containingFile is KtCodeFragment) {
        return KtCodeFragmentModuleImpl(containingFile, moduleByModuleInfo)
    }

    return moduleByModuleInfo
}
