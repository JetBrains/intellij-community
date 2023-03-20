// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtFile

class KotlinExactPackagesIndex internal constructor() : StringStubIndexExtension<KtFile>() {
    companion object {
        @JvmField
        val NAME: StubIndexKey<String, KtFile> = StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinExactPackagesIndex")

        @JvmStatic
        @JvmName("getFiles")
        fun get(fqName: String, project: Project, scope: GlobalSearchScope): Collection<KtFile> {
            return StubIndex.getElements(NAME, fqName, project, scope, KtFile::class.java)
        }
    }

    override fun getKey(): StubIndexKey<String, KtFile> = NAME

    override fun get(fqName: String, project: Project, scope: GlobalSearchScope): Collection<KtFile> {
        return Companion.get(fqName, project, scope)
    }
}