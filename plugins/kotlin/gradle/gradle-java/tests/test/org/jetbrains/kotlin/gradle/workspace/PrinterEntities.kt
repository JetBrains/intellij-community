// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.*
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider

interface PrinterEntity {
    val presentableName: String
}

interface ContributableEntity : PrinterEntity

interface ModulePrinterEntity : ContributableEntity {
    val kotlinFacetSettings: KotlinFacetSettings?
    val orderEntries: List<OrderEntryPrinterEntity>
}

class ModulePrinterEntityImpl(val module: Module) : ModulePrinterEntity {
    override val presentableName: String get() = module.name

    override val kotlinFacetSettings: KotlinFacetSettings? by lazy {
        runReadAction { KotlinFacetSettingsProvider.getInstance(module.project)?.getSettings(module) }
    }

    override val orderEntries by lazy {
        runReadAction { ModuleRootManager.getInstance(module).orderEntries }.map(OrderEntry::toPrinterEntity)
    }
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

internal fun Module.toPrinterEntity(): ModulePrinterEntity = ModulePrinterEntityImpl(this)
internal fun OrderEntry.toPrinterEntity(): OrderEntryPrinterEntity = OrderEntryPrinterEntityImpl(this)
