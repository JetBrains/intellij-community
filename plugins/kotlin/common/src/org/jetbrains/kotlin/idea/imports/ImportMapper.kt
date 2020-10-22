/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.imports

import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.name.FqName


object ImportMapper {
    private data class FqNameWithVersion(val fqName: FqName, val version: ApiVersion)

    private val UTIL_TO_COLLECTIONS get() = listOf(
        "RandomAccess",
        "ArrayList",
        "LinkedHashMap",
        "HashMap",
        "LinkedHashSet",
        "HashSet",
    ).convertListToMap(
        javaPackage = "java.util",
        kotlinPackage = "kotlin.collections",
    )

    private val CONCURRENT_TO_CANCELLATION get() = listOf("CancellationException").convertListToMap(
        javaPackage = "java.util.concurrent",
        kotlinPackage = "kotlin.coroutines.cancellation",
        version = ApiVersion.KOTLIN_1_4,
    )

    private val LANG_TO_TEXT get() = listOf("Appendable", "StringBuilder").convertListToMap(
        javaPackage = "java.lang",
        kotlinPackage = "kotlin.text",
    )

    private val CHARSET_TO_TEXT get() = listOf("CharacterCodingException").convertListToMap(
        javaPackage = "java.nio.charset",
        kotlinPackage = "kotlin.text",
        version = ApiVersion.KOTLIN_1_4,
    )

    private val KOTLIN_JVM_TO_KOTLIN get() = listOf("Throws").convertListToMap(
        javaPackage = "kotlin.jvm",
        kotlinPackage = "kotlin",
        version = ApiVersion.KOTLIN_1_4,
    )

    private val LANG_TO_KOTLIN get() = listOf(
        "Error",
        "Exception",
        "RuntimeException",
        "IllegalArgumentException",
        "IllegalStateException",
        "IndexOutOfBoundsException",
        "UnsupportedOperationException",
        "ArithmeticException",
        "NumberFormatException",
        "NullPointerException",
        "ClassCastException",
        "AssertionError",
    ).convertListToMap(
        javaPackage = "java.lang",
        kotlinPackage = "kotlin",
    )

    private val UTIL_TO_KOTLIN get() = listOf(
        "NoSuchElementException",
        "ConcurrentModificationException",
        "Comparator",
    ).convertListToMap(
        javaPackage = "java.util",
        kotlinPackage = "kotlin",
    )

    private fun List<String>.convertListToMap(
        javaPackage: String,
        kotlinPackage: String,
        version: ApiVersion = ApiVersion.KOTLIN_1_3,
    ): Map<FqName, FqNameWithVersion> = associate {
        FqName("$javaPackage.$it") to FqNameWithVersion(FqName("$kotlinPackage.$it"), version)
    }

    private val javaToKotlinMap: Map<FqName, FqNameWithVersion> = mapOf(
        *(UTIL_TO_COLLECTIONS +
                //CONCURRENT_TO_CANCELLATION + // experimental
                LANG_TO_TEXT +
                CHARSET_TO_TEXT +
                KOTLIN_JVM_TO_KOTLIN +
                LANG_TO_KOTLIN +
                UTIL_TO_KOTLIN).entries.map { it.key to it.value }.toTypedArray()
    )

    @TestOnly
    fun getImport2AliasMap(): Map<FqName, FqName> = javaToKotlinMap.mapValues { it.value.fqName }

    fun findActualKotlinFqName(fqName: FqName, availableVersion: ApiVersion = ApiVersion.LATEST_STABLE): FqName? {
        val fqNameWithVersion = javaToKotlinMap[fqName]
        return if (fqNameWithVersion == null || fqNameWithVersion.version > availableVersion)
            null
        else
            fqNameWithVersion.fqName
    }
}
