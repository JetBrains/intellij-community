// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.Processors

abstract class AbstractStringStubIndexExtension<Psi: PsiElement>(protected val valueClass: Class<Psi>): StringStubIndexExtension<Psi>() {

    /**
     * Note: [processor] should not invoke any indices as it could lead to deadlock. Nested index access is forbidden.
     */
    fun processElements(
        s: String,
        project: Project,
        scope: GlobalSearchScope,
        processor: Processor<in Psi>
    ) {
        StubIndex.getInstance().processElements(key, s, project, scope, valueClass, processor)
    }

    fun processAllElements(
        project: Project,
        scope: GlobalSearchScope,
        filter: (String) -> Boolean = { true },
        processor: Processor<in Psi>
    ) {
        val values = mutableSetOf<Psi>()
        val collectProcessor = Processors.cancelableCollectProcessor(values)
        val stubIndex = StubIndex.getInstance()
        val indexKey = key

        // collect all keys, collect all values those fulfill filter into a single collection, process values after that
        getAllKeys(project).forEach { s ->
            if (filter(s)) {
                if (!stubIndex.processElements(indexKey, s, project, scope, valueClass, collectProcessor)) return
            }
        }
        // process until the 1st negative result of processor
        values.all(processor::process)
    }
}