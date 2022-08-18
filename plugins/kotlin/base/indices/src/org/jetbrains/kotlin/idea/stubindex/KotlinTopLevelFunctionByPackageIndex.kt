// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtNamedFunction

object KotlinTopLevelFunctionByPackageIndex : KotlinStringStubIndexExtension<KtNamedFunction>(KtNamedFunction::class.java) {
    private val KEY: StubIndexKey<String, KtNamedFunction> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionByPackageIndex")

    override fun getKey(): StubIndexKey<String, KtNamedFunction> = KEY

    override fun get(fqName: String, project: Project, scope: GlobalSearchScope): Collection<KtNamedFunction> {
        return StubIndex.getElements(KEY, fqName, project, scope, KtNamedFunction::class.java)
    }
}