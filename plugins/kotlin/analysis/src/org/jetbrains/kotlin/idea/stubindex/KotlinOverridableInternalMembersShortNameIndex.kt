// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.kotlin.psi.KtCallableDeclaration

class KotlinOverridableInternalMembersShortNameIndex private constructor() : StringStubIndexExtension<KtCallableDeclaration>() {
    override fun getKey() = KEY

    override fun get(name: String, project: Project, scope: GlobalSearchScope): Collection<KtCallableDeclaration> {
        return StubIndex.getElements(KEY, name, project, scope, KtCallableDeclaration::class.java)
    }

    companion object {

        @JvmField
        val Instance = KotlinOverridableInternalMembersShortNameIndex()

        private val KEY = KotlinIndexUtil.createIndexKey(KotlinOverridableInternalMembersShortNameIndex::class.java)
    }
}