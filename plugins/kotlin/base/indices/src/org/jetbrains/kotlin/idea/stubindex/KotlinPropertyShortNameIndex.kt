// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class KotlinPropertyShortNameIndex internal constructor() : StringStubIndexExtension<KtNamedDeclaration>() {
    companion object Helper : KotlinStringStubIndexHelper<KtNamedDeclaration>(KtNamedDeclaration::class.java) {
        override val indexKey: StubIndexKey<String, KtNamedDeclaration> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex")
    }

    override fun getKey(): StubIndexKey<String, KtNamedDeclaration> = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinPropertyShortNameIndex[shortName, project, scope]"))
    override fun get(shortName: String, project: Project, scope: GlobalSearchScope): Collection<KtNamedDeclaration> {
        return Helper[shortName, project, scope]
    }
}