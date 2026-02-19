// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.projectStructure.scopes

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopeUtil
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaGlobalSearchScopeMerger
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import java.io.File

/**
 * This test checks specific scenarios which cannot be covered with the project structure tests in [AbstractResolutionScopeStructureTest].
 */
class KotlinGlobalSearchScopeMergerTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File = error("Should not be called")

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    private class UniqueScope : GlobalSearchScope() {
        override fun isSearchInModuleContent(aModule: Module): Boolean = false
        override fun isSearchInLibraries(): Boolean = false
        override fun contains(file: VirtualFile): Boolean = false
    }

    fun `test that non-mergeable intersection scopes don't cause a stack overflow`() {
        val scope1 = UniqueScope()
        val scope2 = UniqueScope()
        val scope3 = UniqueScope()
        val scope4 = UniqueScope()

        val intersectionScope1 = scope1.intersectWith(scope2)
        val intersectionScope2 = scope3.intersectWith(scope4)

        val mergedScope = KaGlobalSearchScopeMerger.getInstance(project).union(listOf(intersectionScope1, intersectionScope2))

        assertEquals(
            "The merged scope should contain the same intersection scopes.",
            setOf(intersectionScope1, intersectionScope2),
            GlobalSearchScopeUtil.flattenUnionScope(mergedScope).toSet(),
        )
    }
}
