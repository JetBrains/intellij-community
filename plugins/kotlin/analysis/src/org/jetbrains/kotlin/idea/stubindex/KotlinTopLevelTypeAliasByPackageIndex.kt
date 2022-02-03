// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtTypeAlias

class KotlinTopLevelTypeAliasByPackageIndex : AbstractStringStubIndexExtension<KtTypeAlias>(KtTypeAlias::class.java) {
    override fun getKey(): StubIndexKey<String, KtTypeAlias> = KEY

    override fun get(s: String, project: Project, scope: GlobalSearchScope): Collection<KtTypeAlias> =
        StubIndex.getElements(KEY, s, project, scope, KtTypeAlias::class.java)

    companion object {
        val KEY = KotlinIndexUtil.createIndexKey(KotlinTopLevelTypeAliasByPackageIndex::class.java)
        val INSTANCE = KotlinTopLevelTypeAliasByPackageIndex()

        @JvmStatic
        fun getInstance() = INSTANCE
    }
}