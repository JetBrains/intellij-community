// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtFile

class KotlinFileFacadeFqNameIndex internal constructor() : StringStubIndexExtension<KtFile>() {
    companion object Helper : KotlinStringStubIndexHelper<KtFile>(KtFile::class.java) {
        override val indexKey: StubIndexKey<String, KtFile> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinFileFacadeFqNameIndex")
    }

    override fun getKey(): StubIndexKey<String, KtFile> = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinFileFacadeFqNameIndex[fqName, project, scope]"))
    override fun get(fqName: String, project: Project, scope: GlobalSearchScope): Collection<KtFile> {
        return Helper[fqName, project, scope]
    }
}