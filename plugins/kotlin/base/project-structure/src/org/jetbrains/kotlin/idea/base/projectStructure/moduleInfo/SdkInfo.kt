// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.SdkInfoBase
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.scope.PoweredLibraryScopeBase
import org.jetbrains.kotlin.idea.base.projectStructure.scope.calculateEntriesVirtualFileSystems
import org.jetbrains.kotlin.idea.base.projectStructure.scope.calculateTopPackageNames
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

//TODO: (module refactoring) there should be separate SdkSourceInfo but there are no kotlin source in existing sdks for now :)
@K1ModeProjectStructureApi
data class SdkInfo(override val project: Project, val sdk: Sdk) : IdeaModuleInfo, SdkInfoBase {
    private val topClassesPackageNames: Set<String>?
    private val entriesVirtualFileSystems: Set<NewVirtualFileSystem>?

    init {
        val classes = runReadAction { sdk.rootProvider.getFiles(OrderRootType.CLASSES) }
        topClassesPackageNames = classes.calculateTopPackageNames()
        entriesVirtualFileSystems = classes.calculateEntriesVirtualFileSystems()
    }

    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.LIBRARY

    override val name: Name = Name.special("<sdk ${sdk.name}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("sdk.0", sdk.name)

    override val contentScope: GlobalSearchScope
        get() = SdkScope(project, topClassesPackageNames, entriesVirtualFileSystems, sdk)

    override fun dependencies(): List<IdeaModuleInfo> = listOf(this)
    override fun dependenciesWithoutSelf(): Sequence<IdeaModuleInfo> = emptySequence()

    override val platform: TargetPlatform
        // TODO(dsavvinov): provide proper target version
        get() = when (sdk.sdkType) {
            is KotlinSdkType -> CommonPlatforms.defaultCommonPlatform
            else -> JvmPlatforms.unspecifiedJvmPlatform
        }

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = JvmPlatformAnalyzerServices
}

fun Project.allSdks(modules: Array<out Module>? = null): Set<Sdk> = runReadAction {
    if (isDisposed) return@runReadAction emptySet()
    val sdks = ProjectJdkTable.getInstance().allJdks.toHashSet()
    val modulesArray = modules ?: ideaModules()
    ProgressManager.checkCanceled()
    modulesArray.flatMapTo(sdks, ::moduleSdks)
    sdks
}

fun moduleSdks(module: Module): List<Sdk> =
    if (module.isDisposed) {
        emptyList()
    } else {
        ModuleRootManager.getInstance(module).orderEntries.mapNotNull { orderEntry ->
            ProgressManager.checkCanceled()
            orderEntry.safeAs<JdkOrderEntry>()?.jdk
        }
    }

//TODO: (module refactoring) android sdk has modified scope
@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide 'calcHashCode()'
private class SdkScope(
    project: Project,
    topPackageNames: Set<String>?,
    entriesVirtualFileSystems: Set<NewVirtualFileSystem>?,
    val sdk: Sdk
) : PoweredLibraryScopeBase(
    project,
    sdk.rootProvider.getFiles(OrderRootType.CLASSES),
    VirtualFile.EMPTY_ARRAY,
    topPackageNames,
    entriesVirtualFileSystems
) {
    override fun equals(other: Any?): Boolean = other is SdkScope && sdk == other.sdk
    override fun calcHashCode(): Int = sdk.hashCode()
    override fun toString(): String = "SdkScope($sdk)"
}
