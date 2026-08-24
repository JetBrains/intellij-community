// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtTypeAlias

@Deprecated(
    message = "Use 'KotlinFullTypeAliasNameIndex' instead",
    replaceWith = ReplaceWith(
        expression = "KotlinFullTypeAliasNameIndex",
        imports = ["org.jetbrains.kotlin.idea.stubindex.KotlinFullTypeAliasNameIndex"],
    )
)
class KotlinTopLevelTypeAliasFqNameIndex internal constructor() : StringStubIndexExtension<KtTypeAlias>() {
    companion object Helper : KotlinStringStubIndexHelper<KtTypeAlias>(KtTypeAlias::class.java) {
        override val indexKey: StubIndexKey<String, KtTypeAlias> get() = KotlinFullTypeAliasNameIndex.indexKey
    }

    override fun getKey(): StubIndexKey<String, KtTypeAlias> = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinFullTypeAliasNameIndex[key, project, scope]"))
    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtTypeAlias> {
        return KotlinFullTypeAliasNameIndex[key, project, scope]
    }
}
