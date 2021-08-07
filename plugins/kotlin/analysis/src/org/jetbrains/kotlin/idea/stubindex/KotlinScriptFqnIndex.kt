// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.kotlin.psi.KtScript

class KotlinScriptFqnIndex private constructor() : StringStubIndexExtension<KtScript>() {
    override fun getKey() = KEY

    override fun get(fqName: String, project: Project, scope: GlobalSearchScope): Collection<KtScript> {
        return StubIndex.getElements(KEY, fqName, project, scope, KtScript::class.java)
    }

    companion object {
        private val KEY = KotlinIndexUtil.createIndexKey(KotlinScriptFqnIndex::class.java)

        @JvmStatic
        val instance = KotlinScriptFqnIndex()
    }
}