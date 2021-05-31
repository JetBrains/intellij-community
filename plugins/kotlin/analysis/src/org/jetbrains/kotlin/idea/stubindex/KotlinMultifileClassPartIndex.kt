// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtFile


class KotlinMultifileClassPartIndex private constructor() : StringStubIndexExtension<KtFile>() {
    override fun getKey(): StubIndexKey<String, KtFile> = KEY

    override fun get(key: String, project: Project, scope: GlobalSearchScope) =
        StubIndex.getElements(KEY, key, project, scope, KtFile::class.java)

    companion object {
        private val KEY = KotlinIndexUtil.createIndexKey(KotlinMultifileClassPartIndex::class.java)

        @JvmField
        val INSTANCE: KotlinMultifileClassPartIndex = KotlinMultifileClassPartIndex()

        @JvmStatic
        fun getInstance(): KotlinMultifileClassPartIndex = INSTANCE
    }
}