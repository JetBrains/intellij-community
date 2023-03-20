// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtCallableDeclaration

class KotlinTopLevelExtensionsByReceiverTypeIndex internal constructor() : StringStubIndexExtension<KtCallableDeclaration>() {
    companion object Helper : KotlinExtensionsByReceiverTypeStubIndexHelper() {
        @JvmField
        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("Use the Helper object instead", level = DeprecationLevel.ERROR)
        val INSTANCE: KotlinTopLevelExtensionsByReceiverTypeIndex = KotlinTopLevelExtensionsByReceiverTypeIndex()

        override val indexKey: StubIndexKey<String, KtCallableDeclaration> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelExtensionsByReceiverTypeIndex")
    }

    override fun getKey() = indexKey

    override fun getVersion(): Int = super.getVersion() + 1

    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtCallableDeclaration> {
        return Helper[key, project, scope]
    }
}