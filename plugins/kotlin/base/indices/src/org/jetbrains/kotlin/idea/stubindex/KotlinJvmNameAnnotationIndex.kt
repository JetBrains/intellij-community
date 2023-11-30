// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtAnnotationEntry

class KotlinJvmNameAnnotationIndex internal constructor() : StringStubIndexExtension<KtAnnotationEntry>() {
    companion object Helper : KotlinStringStubIndexHelper<KtAnnotationEntry>(KtAnnotationEntry::class.java) {
        override val indexKey: StubIndexKey<String, KtAnnotationEntry> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinJvmNameAnnotationIndex")
    }

    override fun getKey(): StubIndexKey<String, KtAnnotationEntry> = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinJvmNameAnnotationIndex[key, project, scope]"))
    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtAnnotationEntry> {
        return Helper[key, project, scope]
    }
}