// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtProperty

object KotlinProbablyNothingPropertyShortNameIndex : StringStubIndexExtension<KtProperty>() {
    private val KEY: StubIndexKey<String, KtProperty> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinProbablyNothingPropertyShortNameIndex")

    override fun getKey(): StubIndexKey<String, KtProperty> = KEY

    override fun get(s: String, project: Project, scope: GlobalSearchScope): Collection<KtProperty> {
        return StubIndex.getElements(KEY, s, project, scope, KtProperty::class.java)
    }
}