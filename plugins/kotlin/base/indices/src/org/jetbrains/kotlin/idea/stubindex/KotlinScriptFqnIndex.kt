// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtScript

@ApiStatus.Internal
class KotlinScriptFqnIndex internal constructor() : StringStubIndexExtension<KtScript>() {
    companion object Helper : KotlinStringStubIndexHelper<KtScript>(KtScript::class.java) {
        override val indexKey: StubIndexKey<String, KtScript> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinScriptFqnIndex")
    }

    override fun getKey() = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinScriptFqnIndex[fqName, project, scope]"))
    override fun get(fqName: String, project: Project, scope: GlobalSearchScope): Collection<KtScript> {
        return Helper[fqName, project, scope]
    }
}