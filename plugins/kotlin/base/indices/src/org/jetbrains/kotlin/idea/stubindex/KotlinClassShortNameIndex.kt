// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtClassOrObject

class KotlinClassShortNameIndex internal constructor() : StringStubIndexExtension<KtClassOrObject>() {
    companion object Helper : KotlinStringStubIndexHelper<KtClassOrObject>(KtClassOrObject::class.java) {
        @JvmField
        @ApiStatus.ScheduledForRemoval
        @Deprecated("Use the Helper object instead", level = DeprecationLevel.ERROR)
        val INSTANCE: KotlinClassShortNameIndex = KotlinClassShortNameIndex()

        override val indexKey: StubIndexKey<String, KtClassOrObject> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex")
    }

    override fun getKey(): StubIndexKey<String, KtClassOrObject> = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinClassShortNameIndex[key, project, scope]"))
    override fun get(shortName: String, project: Project, scope: GlobalSearchScope): Collection<KtClassOrObject> {
        return Helper[shortName, project, scope]
    }

    override fun getVersion(): Int = super.getVersion() + 1

    override fun traceKeyHashToVirtualFileMapping(): Boolean = true
}