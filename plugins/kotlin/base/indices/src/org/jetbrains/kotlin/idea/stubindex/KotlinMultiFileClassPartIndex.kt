// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtFile

/**
 * Map from internal facade class to the facade file
 * "kotlin.LazyKt__LazyKt" -> KtClsFile("LazyKt.class")
 * "kotlin.text.StringsKt___StringsJvmKt" -> KtClsFile("StringsKt.class")
 */
object KotlinMultiFileClassPartIndex : StringStubIndexExtension<KtFile>() {
    private val KEY: StubIndexKey<String, KtFile> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinMultifileClassPartIndex")

    override fun getKey(): StubIndexKey<String, KtFile> = KEY

    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtFile> {
        return StubIndex.getElements(KEY, key, project, scope, KtFile::class.java)
    }
}