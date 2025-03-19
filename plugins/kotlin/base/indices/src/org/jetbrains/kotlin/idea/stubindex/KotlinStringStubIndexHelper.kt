// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.indexing.IdFilter
import org.jetbrains.kotlin.idea.base.indices.getAllKeysAndMeasure
import org.jetbrains.kotlin.idea.base.indices.getByKeyAndMeasure
import org.jetbrains.kotlin.idea.base.indices.processAllKeysAndMeasure
import org.jetbrains.kotlin.idea.base.indices.processElementsAndMeasure

private val isNestedIndexAccessEnabled: Boolean by lazy { Registry.`is`("kotlin.indices.nested.access.enabled") }

abstract class KotlinStringStubIndexHelper<Key : NavigatablePsiElement>(private val valueClass: Class<Key>) {
    private val logger = Logger.getInstance(this.javaClass)
    abstract val indexKey: StubIndexKey<String, Key>

    operator fun get(fqName: String, project: Project, scope: GlobalSearchScope): Collection<Key> {
        return getByKeyAndMeasure(indexKey, logger) { StubIndex.getElements(indexKey, fqName, project, scope, valueClass) }
    }

    fun getAllKeys(project: Project): Collection<String> {
        return getAllKeysAndMeasure(indexKey, logger) { StubIndex.getInstance().getAllKeys(indexKey, project) }
    }

    fun getAllElements(
        key: String,
        project: Project,
        scope: GlobalSearchScope,
        filter: (Key) -> Boolean = { true },
    ): Sequence<Key> {
        val processor = CancelableCollectFilterProcessor<Key>(filter = filter)
        processElements(key, project, scope, null, processor)
        return processor.results.asSequence() // todo move valueFilter out
    }
    /**
     * Note: [processor] should not invoke any indices as it could lead to deadlock. Nested index access is forbidden.
     */
    fun processElements(s: String, project: Project, scope: GlobalSearchScope, processor: Processor<in Key>): Boolean {
        return processElements(s, project, scope, null, processor)
    }

    /**
     * Note: [processor] should not invoke any indices as it could lead to deadlock. Nested index access is forbidden.
     */
    fun processElements(
        key: String,
        project: Project,
        scope: GlobalSearchScope,
        idFilter: IdFilter? = null,
        processor: Processor<in Key>,
    ): Boolean = processElementsAndMeasure(indexKey, logger) {
        StubIndex.getInstance().processElements(indexKey, key, project, scope, idFilter, valueClass, processor)
    }

    inline fun <reified SubKey> getAllElements(
        project: Project,
        scope: GlobalSearchScope,
        noinline keyFilter: (String) -> Boolean = { true },
        noinline valueFilter: (SubKey) -> Boolean = { true },
    ): Sequence<SubKey> {
        val processor = CancelableCollectFilterProcessor<SubKey>(filter = valueFilter)
        processAllElements(project, scope, keyFilter) { key ->
            if (key is SubKey)
                processor.process(key)
            else
                true
        }
        return processor.results.asSequence() // todo move valueFilter out
    }

    fun processAllElements(
        project: Project,
        scope: GlobalSearchScope,
        filter: (String) -> Boolean = { true },
        processor: Processor<in Key>
    ) {
        val stubIndex = StubIndex.getInstance()

        if (isNestedIndexAccessEnabled) {
            stubIndex.processAllKeys(indexKey, project, CancelableDelegateFilterProcessor(filter) { key ->
                // process until the 1st negative result of processor
                stubIndex.processElements(indexKey, key, project, scope, valueClass, processor)
            })
        } else {
            // collect all keys, collect all values those fulfill filter into a single collection, process values after that

            val allKeys = HashSet<String>()
            val processAllKeys = processAllKeysAndMeasure(indexKey, logger) {
                stubIndex.processAllKeys(indexKey, project, CancelableCollectFilterProcessor(allKeys, filter))
            }
            if (!processAllKeys) return

            if (allKeys.isNotEmpty()) {
                val values = HashSet<Key>(allKeys.size)
                val collectProcessor = Processors.cancelableCollectProcessor(values)
                allKeys.forEach { s ->
                    val processElements = processElementsAndMeasure(indexKey, logger) {
                        stubIndex.processElements(indexKey, s, project, scope, valueClass, collectProcessor)
                    }
                    if (!processElements) return
                }
                // process until the 1st negative result of processor
                values.all(processor::process)
            }
        }
    }

    fun processAllKeys(scope: GlobalSearchScope, filter: IdFilter? = null, processor: Processor<in String>): Boolean {
        return processAllKeysAndMeasure(indexKey, logger) {
            StubIndex.getInstance().processAllKeys(indexKey, processor, scope, filter)
        }
    }

    fun processAllKeys(project: Project, processor: Processor<in String>): Boolean {
        return processAllKeysAndMeasure(indexKey, logger) {
            StubIndex.getInstance().processAllKeys(indexKey, project, processor)
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
    collection: Collection<T> = mutableListOf(),
    private val filter: (T) -> Boolean,
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