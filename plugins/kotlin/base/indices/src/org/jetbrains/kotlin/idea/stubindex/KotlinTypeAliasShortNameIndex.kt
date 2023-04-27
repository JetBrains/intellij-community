// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtTypeAlias

class KotlinTypeAliasShortNameIndex internal constructor() : StringStubIndexExtension<KtTypeAlias>() {
    companion object Helper : KotlinStringStubIndexHelper<KtTypeAlias>(KtTypeAlias::class.java) {
        override val indexKey: StubIndexKey<String, KtTypeAlias> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex")
    }

    override fun getKey(): StubIndexKey<String, KtTypeAlias> = indexKey

    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtTypeAlias> {
        return Helper[key, project, scope]
    }
}