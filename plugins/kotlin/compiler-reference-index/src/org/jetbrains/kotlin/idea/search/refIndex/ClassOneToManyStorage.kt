// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.containers.generateRecursiveSequence
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.externalizer.StringCollectionExternalizer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@IntellijInternalApi
class ClassOneToManyStorage(storagePath: Path) {
    init {
        val storageName = storagePath.name
        storagePath.parent.listDirectoryEntries("$storageName*").ifNotEmpty {
            forEach { it.deleteIfExists() }
            LOG.warn("'$storageName' was deleted")
        }
    }

    private val storage = PersistentMapBuilder.newBuilder(
      storagePath,
      EnumeratorStringDescriptor.INSTANCE,
      externalizer,
    ).build()

    fun closeAndClean(): Unit = storage.closeAndClean()

    fun put(key: String, values: Collection<String>) {
        storage.put(key, values)
    }

    operator fun get(key: FqName, deep: Boolean): Sequence<FqName> = get(key.asString(), deep).map(::FqName)
    operator fun get(key: String, deep: Boolean): Sequence<String> {
        val values = storage[key]?.asSequence() ?: emptySequence()
        if (!deep) return values

        return generateRecursiveSequence(values) {
          storage[it]?.asSequence() ?: emptySequence()
        }
    }

    companion object {
        private val externalizer = StringCollectionExternalizer<Collection<String>>(::ArrayList)
        private val LOG = logger<ClassOneToManyStorage>()
    }
}