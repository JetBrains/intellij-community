// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.textBuilder

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.utils.keysToMap

data class DecompiledText(val text: String, val index: DecompiledTextIndex)

interface DecompiledTextIndexer<out T : Any> {
    fun indexDescriptor(descriptor: DeclarationDescriptor): Collection<T>
}

// In-memory HashMap-based index of decompiled text
// allows navigation features
class DecompiledTextIndex(private val indexers: Collection<DecompiledTextIndexer<*>>) {
    private val indexerToMap: Map<DecompiledTextIndexer<*>, MutableMap<Any, TextRange>> = indexers.keysToMap { hashMapOf<Any, TextRange>() }

    fun addToIndex(descriptor: DeclarationDescriptor, textRange: TextRange) {
        indexers.forEach { mapper ->
            val correspondingMap = indexerToMap.getValue(mapper)
            mapper.indexDescriptor(descriptor).forEach { key ->
                correspondingMap[key] = textRange
            }
        }
    }

    fun <T : Any> getRange(mapper: DecompiledTextIndexer<T>, key: T): TextRange? = indexerToMap[mapper]?.get(key)

    companion object {
        val Empty = DecompiledTextIndex(listOf())
    }
}
