// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.roots.*

interface PrinterEntity {
    val presentableName: String
}

enum class OrderEntryKind {
    MODULE, LIBRARY, SDK;
}

interface OrderEntryPrinterEntity : PrinterEntity {
    val kind: OrderEntryKind?
}

class OrderEntryPrinterEntityImpl(private val orderEntry: OrderEntry): OrderEntryPrinterEntity {
    override val presentableName: String get() {
        val exportableOrderEntryPostfix = if (orderEntry is ExportableOrderEntry) {
            " (scope: ${orderEntry.scope.name}, exported: ${orderEntry.isExported})"
        } else ""

        return "${orderEntry.presentableName}$exportableOrderEntryPostfix"
    }


    override val kind: OrderEntryKind? = when (orderEntry) {
        is LibraryOrderEntry -> OrderEntryKind.LIBRARY
        is ModuleOrderEntry -> OrderEntryKind.MODULE
        is JdkOrderEntry -> OrderEntryKind.SDK
        else -> null
    }
}

internal fun OrderEntry.toPrinterEntity(): OrderEntryPrinterEntity = OrderEntryPrinterEntityImpl(this)
