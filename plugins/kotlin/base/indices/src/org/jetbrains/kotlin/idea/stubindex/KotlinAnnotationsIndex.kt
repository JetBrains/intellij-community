// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtAnnotationEntry

object KotlinAnnotationsIndex : KotlinStringStubIndexExtension<KtAnnotationEntry>(KtAnnotationEntry::class.java) {
    private val KEY: StubIndexKey<String, KtAnnotationEntry> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex")

    override fun getKey(): StubIndexKey<String, KtAnnotationEntry> = KEY

    override fun getVersion(): Int = super.getVersion() + 1

    override fun get(s: String, project: Project, scope: GlobalSearchScope): Collection<KtAnnotationEntry> {
        return StubIndex.getElements(KEY, s, project, scope, KtAnnotationEntry::class.java)
    }

    @JvmStatic
    @Deprecated("Use KotlinAnnotationsIndex as an object.", ReplaceWith("KotlinAnnotationsIndex"))
    fun getInstance(): KotlinAnnotationsIndex = KotlinAnnotationsIndex
}