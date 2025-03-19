// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtAnnotationEntry

class KotlinAnnotationsIndex internal constructor() : StringStubIndexExtension<KtAnnotationEntry>() {
    companion object Helper : KotlinStringStubIndexHelper<KtAnnotationEntry>(KtAnnotationEntry::class.java) {
        @JvmField
        @Deprecated("Use the Helper object instead", level = DeprecationLevel.HIDDEN)
        val INSTANCE: KotlinAnnotationsIndex = KotlinAnnotationsIndex()

        override val indexKey: StubIndexKey<String, KtAnnotationEntry> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex")
    }

    override fun getKey(): StubIndexKey<String, KtAnnotationEntry> = indexKey

    override fun getVersion(): Int = super.getVersion() + 1

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinAnnotationsIndex[key, project, scope]"))
    override fun get(shortName: String, project: Project, scope: GlobalSearchScope): Collection<KtAnnotationEntry> {
        return Helper[shortName, project, scope]
    }
}