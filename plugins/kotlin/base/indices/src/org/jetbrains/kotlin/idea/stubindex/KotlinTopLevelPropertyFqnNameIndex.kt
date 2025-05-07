// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtProperty

class KotlinTopLevelPropertyFqnNameIndex internal constructor() : StringStubIndexExtension<KtProperty>() {
    companion object Helper : KotlinStringStubIndexHelper<KtProperty>(KtProperty::class.java) {
        override val indexKey: StubIndexKey<String, KtProperty> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex")
    }

    override fun getKey(): StubIndexKey<String, KtProperty> = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinTopLevelPropertyFqnNameIndex[key, project, scope]"))
    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtProperty> {
        return Helper[key, project, scope]
    }

    override fun getVersion(): Int = super.getVersion() + 1

    override fun traceKeyHashToVirtualFileMapping(): Boolean = true

}