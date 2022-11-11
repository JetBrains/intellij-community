// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtTypeAlias

object KotlinTypeAliasByExpansionShortNameIndex : StringStubIndexExtension<KtTypeAlias>() {
    val KEY: StubIndexKey<String, KtTypeAlias> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex")

    override fun getKey(): StubIndexKey<String, KtTypeAlias> = KEY

    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtTypeAlias> {
        return StubIndex.getElements(KEY, key, project, scope, KtTypeAlias::class.java)
    }

    @JvmField
    @Suppress("REDECLARATION")
    val Companion: Companion = getJavaClass<Companion>().getField("INSTANCE").get(null) as Companion

    @Suppress("REDECLARATION")
    object Companion {
        @Deprecated(
            "Use KotlinTypeAliasByExpansionShortNameIndex as object instead.",
            ReplaceWith("KotlinTypeAliasByExpansionShortNameIndex"),
            DeprecationLevel.ERROR
        )
        @JvmStatic
        fun getINSTANCE() = KotlinTypeAliasByExpansionShortNameIndex
    }
}

private inline fun <reified T: Any> getJavaClass(): Class<T> = T::class.java