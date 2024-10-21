// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

@K1ModeProjectStructureApi
interface SourceForBinaryModuleInfo : IdeaModuleInfo {
    val binariesModuleInfo: BinaryModuleInfo
    fun sourceScope(): GlobalSearchScope

    // module infos for library source do not have contents in the following sense:
    // we can not provide a collection of files that is supposed to be analyzed in IDE independently
    //
    // as of now each source file is analyzed separately and depends on corresponding binaries
    // see KotlinCacheServiceImpl#createFacadeForSyntheticFiles
    override val contentScope: GlobalSearchScope
        get() = GlobalSearchScope.EMPTY_SCOPE

    override fun dependencies() = listOf(this) + binariesModuleInfo.dependencies()
    override fun dependenciesWithoutSelf(): Sequence<IdeaModuleInfo> = binariesModuleInfo.dependencies().asSequence()

    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.OTHER
}