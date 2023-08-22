// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtCallableDeclaration

class KotlinOverridableInternalMembersShortNameIndex internal constructor() : StringStubIndexExtension<KtCallableDeclaration>() {
    companion object Helper : KotlinStringStubIndexHelper<KtCallableDeclaration>(KtCallableDeclaration::class.java) {
        override val indexKey: StubIndexKey<String, KtCallableDeclaration> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinOverridableInternalMembersShortNameIndex")
    }

    override fun getKey() = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinOverridableInternalMembersShortNameIndex[name, project, scope]"))
    override fun get(name: String, project: Project, scope: GlobalSearchScope): Collection<KtCallableDeclaration> {
        return Helper[name, project, scope]
    }
}