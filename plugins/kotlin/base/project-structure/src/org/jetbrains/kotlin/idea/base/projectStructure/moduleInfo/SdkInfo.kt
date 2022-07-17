// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.scope.PoweredLibraryScopeBase
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices

//TODO: (module refactoring) there should be separate SdkSourceInfo but there are no kotlin source in existing sdks for now :)
data class SdkInfo(override val project: Project, val sdk: Sdk) : IdeaModuleInfo, SdkInfoBase {
    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.LIBRARY

    override val name: Name = Name.special("<sdk ${sdk.name}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("sdk.0", sdk.name)

    override val contentScope: GlobalSearchScope
        get() = SdkScope(project, sdk)

    override fun dependencies(): List<IdeaModuleInfo> = listOf(this)

    override val platform: TargetPlatform
        // TODO(dsavvinov): provide proper target version
        get() = when (sdk.sdkType) {
            is KotlinSdkType -> CommonPlatforms.defaultCommonPlatform
            else -> JvmPlatforms.unspecifiedJvmPlatform
        }

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = JvmPlatformAnalyzerServices

    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() = when (this.sdk.sdkType) {
            is JavaSdk -> super<IdeaModuleInfo>.capabilities + mapOf(JDK_CAPABILITY to true)
            else -> super<IdeaModuleInfo>.capabilities
        }
}

//TODO: (module refactoring) android sdk has modified scope
@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide 'calcHashCode()'
private class SdkScope(
    project: Project,
    val sdk: Sdk
) : PoweredLibraryScopeBase(project, sdk.rootProvider.getFiles(OrderRootType.CLASSES), arrayOf()) {
    override fun equals(other: Any?) = other is SdkScope && sdk == other.sdk
    override fun calcHashCode(): Int = sdk.hashCode()
    override fun toString() = "SdkScope($sdk)"
}