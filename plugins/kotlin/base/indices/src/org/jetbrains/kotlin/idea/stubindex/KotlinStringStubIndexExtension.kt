// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.indexing.IdFilter

abstract class KotlinStringStubIndexExtension<Psi : PsiElement>(private val valueClass: Class<Psi>) : StringStubIndexExtension<Psi>() {
    fun processElements(s: String, project: Project, scope: GlobalSearchScope, processor: Processor<in Psi>): Boolean {
        return processElements(s, project, scope, null, processor)
    }

    fun processElements(s: String, project: Project, scope: GlobalSearchScope, idFilter: IdFilter? = null, processor: Processor<in Psi>): Boolean {
        return StubIndex.getInstance().processElements(key, s, project, scope, idFilter, valueClass, processor)
    }

    fun processAllElements(
        project: Project,
        scope: GlobalSearchScope,
        filter: (String) -> Boolean = { true },
        processor: Processor<in Psi>
    ) {
        val stubIndex = StubIndex.getInstance()
        val indexKey = key

        // collect all keys, collect all values those fulfill filter into a single collection, process values after that

        processAllKeys(project, CancelableDelegateFilterProcessor(filter) { key ->
            // process until the 1st negative result of processor
            stubIndex.processElements(indexKey, key, project, scope, valueClass, processor)
        })
    }
}

class CancelableDelegateFilterProcessor<T>(
    private val filter: (T) -> Boolean,
    private val delegate: Processor<T>
) : Processor<T> {
    override fun process(t: T): Boolean {
        ProgressManager.checkCanceled()
        return if (filter(t)) {
            delegate.process(t)
        } else {
            true
        }
    }

}