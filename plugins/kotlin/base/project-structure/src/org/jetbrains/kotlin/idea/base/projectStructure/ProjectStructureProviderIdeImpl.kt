// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ConcurrentFactoryMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptDependencyModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
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
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

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

        // Potentially, we can use any contextualModule,
        // but we select only those modules that can affect the calculation of the result
        // to improve the cache hit rate and reduce it's size
        val crucialContextualModule = when (contextualModule) {
            // Only info-based modules can be used during search
            !is KtModuleByModuleInfoBase -> null

            // KTIJ-27174: to distinguish between script and regular libraries
            is KtScriptModule -> contextualModule

            // KTIJ-27159: to distinguish between libraries with the same content
            is KtSourceModule -> contextualModule
            else -> null
        }

        return if (anchorElement != null) {
            cachedKtModule(anchorElement, crucialContextualModule)
        } else {
            calculateKtModule(element, crucialContextualModule)
        }
    }

    companion object {
        // TODO maybe introduce some cache?
        fun getKtModuleByModuleInfo(moduleInfo: ModuleInfo): KtModule = createKtModuleByModuleInfo(moduleInfo)
    }
}

private fun <T> cachedKtModule(
    anchorElement: PsiElement,
    contextualModule: T?,
): KtModule where T : KtModule, T : KtModuleByModuleInfoBase {
    val contextToKtModule = CachedValuesManager.getCachedValue(anchorElement) {
        val project = anchorElement.project
        CachedValueProvider.Result.create(
            ConcurrentFactoryMap.createMap<T?, KtModule> { context ->
                calculateKtModule(anchorElement, context)
            },
            ProjectRootModificationTracker.getInstance(project),
            JavaLibraryModificationTracker.getInstance(project),
            KotlinModificationTrackerFactory.getInstance(project).createProjectWideOutOfBlockModificationTracker(),
        )
    }

    return contextToKtModule[contextualModule] ?: errorWithAttachment("No ${KtModule::class.simpleName} found") {
        withPsiEntry("anchorElement", anchorElement)
        withEntry("contextualModule", contextualModule.toString())
    }
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

private fun <T> calculateKtModule(
    psiElement: PsiElement,
    contextualModule: T? = null
): KtModule where T : KtModule, T : KtModuleByModuleInfoBase {
    val containingFile = psiElement.containingFile
    val virtualFile = containingFile?.virtualFile
    val project = psiElement.project
    val config = ModuleInfoProvider.Configuration(
        createSourceLibraryInfoForLibraryBinaries = false,
        preferModulesFromExtensions = (contextualModule is KtScriptModule) || (virtualFile?.nameSequence?.endsWith(".kts") == true),
        contextualModuleInfo = contextualModule?.ideaModuleInfo,
    )

    val infoProvider = ModuleInfoProvider.getInstance(project)
    val moduleInfo = infoProvider.firstOrNull(psiElement, config)
        ?: contextualModule?.let { config.copy(contextualModuleInfo = null) }?.let { infoProvider.firstOrNull(psiElement, it) }
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
