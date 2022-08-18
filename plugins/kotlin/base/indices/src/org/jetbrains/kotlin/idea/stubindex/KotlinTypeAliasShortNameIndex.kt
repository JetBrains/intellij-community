// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtTypeAlias

object KotlinTypeAliasShortNameIndex : KotlinStringStubIndexExtension<KtTypeAlias>(KtTypeAlias::class.java) {
    private val KEY: StubIndexKey<String, KtTypeAlias> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex")

    override fun getKey(): StubIndexKey<String, KtTypeAlias> = KEY

    override fun get(s: String, project: Project, scope: GlobalSearchScope): Collection<KtTypeAlias> {
        return StubIndex.getElements(KEY, s, project, scope, KtTypeAlias::class.java)
    }
}