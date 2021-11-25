// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.kotlin.psi.KtCallableDeclaration

abstract class KotlinExtensionsByReceiverTypeIndex : AbstractStringStubIndexExtension<KtCallableDeclaration>(KtCallableDeclaration::class.java) {
    fun buildKey(receiverTypeName: String, callableName: String): String = receiverTypeName + SEPARATOR + callableName

    fun receiverTypeNameFromKey(key: String): String = key.substringBefore(SEPARATOR, "")

    fun callableNameFromKey(key: String): String = key.substringAfter(SEPARATOR, "")

    final override fun get(s: String, project: Project, scope: GlobalSearchScope): Collection<KtCallableDeclaration> =
      StubIndex.getElements(key, s, project, scope, KtCallableDeclaration::class.java)

    private companion object {
        private const val SEPARATOR = '\n'
    }
}