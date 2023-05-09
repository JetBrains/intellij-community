// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import java.util.*

object ImportFixHelper {
    enum class ImportKind(private val key: String, val groupedByPackage: Boolean = false) {
        CLASS("text.class.0", true),
        PROPERTY("text.property.0"),
        OBJECT("text.object.0", true),
        FUNCTION("text.function.0"),
        EXTENSION_PROPERTY("text.extension.property.0"),
        EXTENSION_FUNCTION("text.extension.function.0"),
        OPERATOR("text.operator.0");

        fun toText(number: Int) = KotlinBundle.message(key, if (number == 1) 1 else 2)
    }

    class ImportInfo<T : Comparable<T>>(val kind: ImportKind, val name: String, val priority: T)

    @IntentionName
    fun <T : Comparable<T>> calculateTextForFix(importInfos: Iterable<ImportInfo<T>>, suggestions: Iterable<FqName>): String {
        val importNamesGroupedByKind = importInfos.groupBy(keySelector = { it.kind }) { it }

        return if (importNamesGroupedByKind.size == 1) {
            val (kind, names) = importNamesGroupedByKind.entries.first()
            val sortedImportInfos = TreeSet<ImportInfo<T>>(compareBy({ it.priority }, { it.name }))
            sortedImportInfos.addAll(names)
            val firstName = sortedImportInfos.first().name
            val singlePackage = suggestions.groupBy { it.parentOrNull() ?: FqName.ROOT }.size == 1

            if (singlePackage) {
                val sortedByName = sortedImportInfos.toSortedSet(compareBy { it.name })
                val size = sortedByName.size
                if (size == 2) {
                    KotlinBundle.message(
                        "fix.import.kind.0.name.1.and.name.2",
                        kind.toText(size),
                        sortedByName.first().name,
                        sortedByName.last().name
                    )
                } else {
                    KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(size), firstName, size - 1)
                }
            } else if (kind.groupedByPackage) {
                KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(1), firstName, 0)
            } else {
                val groupBy = sortedImportInfos.map { it.name }.toSortedSet().groupBy { it.substringBefore('.') }
                val value = groupBy.entries.first().value
                val first = value.first()
                val multiple = if (value.size == 1) 0 else 1
                when {
                    groupBy.size != 1 -> KotlinBundle.message(
                        "fix.import.kind.0.name.1.2",
                        kind.toText(1),
                        first.substringAfter('.'),
                        multiple
                    )

                    value.size == 2 -> KotlinBundle.message(
                        "fix.import.kind.0.name.1.and.name.2",
                        kind.toText(value.size),
                        first,
                        value.last()
                    )

                    else -> KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(1), first, multiple)
                }
            }
        } else {
            KotlinBundle.message("fix.import")
        }
    }
}