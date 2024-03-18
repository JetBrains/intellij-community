// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtProperty

@ApiStatus.Internal
class KotlinProbablyNothingPropertyShortNameIndex internal constructor() : StringStubIndexExtension<KtProperty>() {
    companion object Helper : KotlinStringStubIndexHelper<KtProperty>(KtProperty::class.java) {
        override val indexKey: StubIndexKey<String, KtProperty> =
            StubIndexKey.createIndexKey(KotlinProbablyNothingPropertyShortNameIndex::class.java.simpleName)
    }

    override fun getKey(): StubIndexKey<String, KtProperty> = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinProbablyNothingPropertyShortNameIndex[shortName, project, scope]"))
    override fun get(shortName: String, project: Project, scope: GlobalSearchScope): Collection<KtProperty> {
        return Helper[shortName, project, scope]
    }
}