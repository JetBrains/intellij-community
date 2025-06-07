// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtClassOrObject

class KotlinSuperClassIndex internal constructor() : StringStubIndexExtension<KtClassOrObject>() {
    companion object Helper : KotlinStringStubIndexHelper<KtClassOrObject>(KtClassOrObject::class.java) {
        override val indexKey: StubIndexKey<String, KtClassOrObject> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex")
    }

    override fun getKey(): StubIndexKey<String, KtClassOrObject> = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinSuperClassIndex[key, project, scope]"))
    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtClassOrObject> {
        return Helper[key, project, scope]
    }
}