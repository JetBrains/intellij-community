// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEntry

class OrderEntryPrinterContributor : ModulePrinterContributor {
    override fun PrinterContext.process(module: Module) = with(printer) {
        val orderEntries = runReadAction { ModuleRootManager.getInstance(module).orderEntries }
            .map(OrderEntry::toPrinterEntity)
        val sanitizedEntries = replaceNativeDistributionOrderEntries(orderEntries)

        if (sanitizedEntries.isEmpty()) return

        println("Order entries:")
        indented {
            for (entry in sanitizedEntries) {
                println(entry.presentableName)
            }
        }
    }
}
