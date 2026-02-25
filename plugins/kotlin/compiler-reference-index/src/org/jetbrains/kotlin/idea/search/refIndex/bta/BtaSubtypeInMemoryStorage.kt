// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.util.containers.generateRecursiveSequence
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain.Companion.cri
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes

internal class BtaSubtypeInMemoryStorage private constructor(
    // TODO KTIJ-37735: use persistent hash map to avoid retaining all CRI data in memory
    private val subtypes: Map<Int, Collection<String>>,
) {
    operator fun get(key: FqName, deep: Boolean): Sequence<FqName> = getSubtypes(key, deep).map(::FqName)

    private fun getSubtypes(key: FqName, deep: Boolean): Sequence<String> {
        val values = subtypes[key.hashCode()]?.asSequence() ?: return emptySequence()
        if (!deep) return values

        return generateRecursiveSequence(values) {
            subtypes[it.hashCode()]?.asSequence() ?: emptySequence()
        }
    }

    @OptIn(ExperimentalBuildToolsApi::class)
    companion object {
        fun create(criRoot: Path): BtaSubtypeInMemoryStorage? {
            if (!criRoot.hasSubtypeData()) return null

            val subtypesData = criRoot.resolve(CriToolchain.SUBTYPES_FILENAME).readBytes()

            val toolchains = KotlinToolchains.loadImplementation(ClassLoader.getSystemClassLoader())
            val subtypeEntries = toolchains.createBuildSession().use { session ->
                session.executeOperation(
                    session.kotlinToolchains.cri.createCriSubtypeDataDeserializationOperation(subtypesData)
                )
            }

            val subtypes = mutableMapOf<Int, MutableSet<String>>()
            subtypeEntries.forEach { entry ->
                val k = entry.fqNameHashCode ?: return@forEach
                subtypes.getOrPut(k) { mutableSetOf() }.addAll(entry.subtypes)
            }

            if (subtypes.isEmpty()) return null
            return BtaSubtypeInMemoryStorage(subtypes)
        }
    }
}

@OptIn(ExperimentalBuildToolsApi::class)
internal fun Path.hasSubtypeData(): Boolean = resolve(CriToolchain.SUBTYPES_FILENAME).exists()
