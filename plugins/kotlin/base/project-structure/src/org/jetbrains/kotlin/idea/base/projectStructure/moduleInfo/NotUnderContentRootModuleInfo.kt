// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.NonSourceModuleInfoBase
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.idea.caches.project.NotUnderContentRootModuleInfo as OldNotUnderContentRootModuleInfo

object NotUnderContentRootModuleInfo : OldNotUnderContentRootModuleInfo(), IdeaModuleInfo, NonSourceModuleInfoBase {
    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.OTHER

    override val name: Name = Name.special("<special module for files not under source root>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("special.module.for.files.not.under.source.root")

    override val project: Project?
        get() = null

    override val contentScope: GlobalSearchScope
        get() = GlobalSearchScope.EMPTY_SCOPE

    //TODO: (module refactoring) dependency on runtime can be of use here
    override fun dependencies(): List<IdeaModuleInfo> = listOf(this)
    override fun dependenciesWithoutSelf(): Sequence<IdeaModuleInfo> = emptySequence()

    override val platform: TargetPlatform
        get() = JvmPlatforms.defaultJvmPlatform

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = platform.single().findAnalyzerServices()
}