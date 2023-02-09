// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.SmartList
import com.intellij.util.indexing.IdFilter

abstract class KotlinStringStubIndexExtension<PsiNav : NavigatablePsiElement>(private val valueClass: Class<PsiNav>) : StringStubIndexExtension<PsiNav>() {
    fun getAllElements(s: String, project: Project, scope: GlobalSearchScope, filter: (PsiNav) -> Boolean): List<PsiNav> {
        val values = SmartList<PsiNav>()
        processElements(s, project, scope, null, CancelableCollectFilterProcessor(values, filter))
        return values
    }
    /**
     * Note: [processor] should not invoke any indices as it could lead to deadlock. Nested index access is forbidden.
     */
    fun processElements(s: String, project: Project, scope: GlobalSearchScope, processor: Processor<in PsiNav>): Boolean {
        return processElements(s, project, scope, null, processor)
    }

    /**
     * Note: [processor] should not invoke any indices as it could lead to deadlock. Nested index access is forbidden.
     */
    fun processElements(s: String, project: Project, scope: GlobalSearchScope, idFilter: IdFilter? = null, processor: Processor<in PsiNav>): Boolean {
        return StubIndex.getInstance().processElements(key, s, project, scope, idFilter, valueClass, processor)
    }

    fun getAllElements(
        project: Project,
        scope: GlobalSearchScope,
        keyFilter: (String) -> Boolean = { true },
        valueFilter: (PsiNav) -> Boolean
    ): List<PsiNav> {
        val values = SmartList<PsiNav>()
        processAllElements(project, scope, keyFilter, CancelableCollectFilterProcessor(values, valueFilter))
        return values
    }

    fun processAllElements(
        project: Project,
        scope: GlobalSearchScope,
        filter: (String) -> Boolean = { true },
        processor: Processor<in PsiNav>
    ) {
        val stubIndex = StubIndex.getInstance()
        val indexKey = key

        if (isNestedIndexAccessEnabled) {
            processAllKeys(project, CancelableDelegateFilterProcessor(filter) { key ->
                // process until the 1st negative result of processor
                stubIndex.processElements(indexKey, key, project, scope, valueClass, processor)
            })
        } else {
            // collect all keys, collect all values those fulfill filter into a single collection, process values after that

            val allKeys = HashSet<String>()
            if (!processAllKeys(project, CancelableCollectFilterProcessor(allKeys, filter))) return

            if (allKeys.isNotEmpty()) {
                val values = HashSet<PsiNav>(allKeys.size)
                val collectProcessor = Processors.cancelableCollectProcessor(values)
                allKeys.forEach { s ->
                    if (!stubIndex.processElements(indexKey, s, project, scope, valueClass, collectProcessor)) return
                }
                // process until the 1st negative result of processor
                values.all(processor::process)
            }
        }
    }

    fun processAllKeys(scope: GlobalSearchScope, filter: IdFilter? = null, processor: Processor<in String>): Boolean =
        StubIndex.getInstance().processAllKeys(key, processor, scope, filter)

    companion object {
        val isNestedIndexAccessEnabled: Boolean by lazy {
            Registry.`is`("kotlin.indices.nested.access.enabled")
        }
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
    companion object {
        val ALWAYS_TRUE: (Any) -> Boolean = { true }
    }
}

class CancelableCollectFilterProcessor<T>(
    collection: Collection<T>,
    private val filter: (T) -> Boolean
) : CommonProcessors.CollectProcessor<T>(collection) {
    override fun process(t: T): Boolean {
        ProgressManager.checkCanceled()
        return super.process(t)
    }

    override fun accept(t: T): Boolean = filter(t)

    companion object {
        val ALWAYS_TRUE: (Any) -> Boolean = { true }
    }
}