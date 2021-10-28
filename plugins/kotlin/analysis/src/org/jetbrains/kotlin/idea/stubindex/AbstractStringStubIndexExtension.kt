// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor

abstract class AbstractStringStubIndexExtension<Psi: PsiElement>(protected val valueClass: Class<Psi>): StringStubIndexExtension<Psi>() {

    fun processAllElements(
        project: Project,
        scope: GlobalSearchScope,
        processor: Processor<in Psi>
    ) {
        processAllElements(project, scope, { true }, processor)
    }

    fun processAllElements(
        project: Project,
        scope: GlobalSearchScope,
        filter: (String) -> Boolean,
        processor: Processor<in Psi>
    ) {
        val stubIndex = StubIndex.getInstance()
        val indexKey = key
        getAllKeys(project).forEach { s ->
            if (filter(s)) {
                if (!stubIndex.processElements(indexKey, s, project, scope, valueClass, processor)) return
            }
        }
    }
}