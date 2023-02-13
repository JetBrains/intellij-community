// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtAnnotationEntry

object KotlinJvmNameAnnotationIndex : KotlinStringStubIndexExtension<KtAnnotationEntry>(KtAnnotationEntry::class.java) {
    private val KEY: StubIndexKey<String, KtAnnotationEntry> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinJvmNameAnnotationIndex")

    override fun getKey(): StubIndexKey<String, KtAnnotationEntry> = KEY

    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtAnnotationEntry> =
        StubIndex.getElements(KEY, key, project, scope, KtAnnotationEntry::class.java)
}