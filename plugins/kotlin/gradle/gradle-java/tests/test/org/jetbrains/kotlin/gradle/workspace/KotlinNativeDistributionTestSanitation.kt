// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

internal fun replaceNativeDistributionOrderEntries(orderEntries: Collection<OrderEntryPrinterEntity>): Collection<OrderEntryPrinterEntity> =
    replaceNativeDistribution(orderEntries, fakeOrderEntryEntityForKotlinNativeDistribution)

private fun <T : PrinterEntity> replaceNativeDistribution(elements: Collection<T>, replacement: T): Collection<T> {
    val nativeDistributionEntries = elements.filterTo(mutableSetOf()) {
        NATIVE_DISTRIBUTION_LIBRARY_PATTERN.matches(it.presentableName)
    }

    if (nativeDistributionEntries.isEmpty())
        return elements.toList()

    return elements - nativeDistributionEntries + replacement
}

private val NATIVE_DISTRIBUTION_LIBRARY_PATTERN = "^Kotlin/Native.*".toRegex()
private const val KOTLIN_NATIVE_DISTRIBUTION_REPLACEMENT_NAME = "<Kotlin/Native Distribution Libraries Test Stub>"

private val fakeOrderEntryEntityForKotlinNativeDistribution = object : OrderEntryPrinterEntity {
    override val kind: OrderEntryKind
        get() = OrderEntryKind.LIBRARY
    override val presentableName: String
        get() = KOTLIN_NATIVE_DISTRIBUTION_REPLACEMENT_NAME

}
