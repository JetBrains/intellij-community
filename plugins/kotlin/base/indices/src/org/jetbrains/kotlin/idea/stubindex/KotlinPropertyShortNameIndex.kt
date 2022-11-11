// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtNamedDeclaration

object KotlinPropertyShortNameIndex : KotlinStringStubIndexExtension<KtNamedDeclaration>(KtNamedDeclaration::class.java) {
    private val KEY: StubIndexKey<String, KtNamedDeclaration> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex")

    override fun getKey(): StubIndexKey<String, KtNamedDeclaration> = KEY

    override fun get(s: String, project: Project, scope: GlobalSearchScope): Collection<KtNamedDeclaration> {
        return StubIndex.getElements(KEY, s, project, scope, KtNamedDeclaration::class.java)
    }
}