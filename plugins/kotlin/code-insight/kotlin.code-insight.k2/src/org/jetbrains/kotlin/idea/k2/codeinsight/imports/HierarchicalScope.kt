// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeContext
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.name.Name

internal class HierarchicalScope private constructor(private val scopes: List<KaScope>) {

    /**
     * Finds classifier symbols corresponding to the given name across the scope layers.
     *
     * Symbols from higher priority scope layers are returned first.
     *
     * Each list in the sequence represents classifiers found in a single scopes layer.
     */
    fun findClassifiers(name: Name): Sequence<List<KaClassifierSymbol>> {
        return scopes.asSequence()
            .map { it.classifiers(name).toList() }
            .filter { it.isNotEmpty() }
    }

    companion object {
        fun KaSession.createFrom(scopeContext: KaScopeContext): HierarchicalScope {
            val scopeGroupsSorted = scopeContext.scopes
                .groupBy({ it.kind }, { it.scope })
                .toSortedMap(compareByDescending { it.indexInTower })

            val scopes = scopeGroupsSorted.values.map { it.asCompositeScope() }

            return HierarchicalScope(scopes)
        }
    }
}