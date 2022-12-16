// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.facet.externalSystemTestRunTasks

class TestTasksContributor : ModulePrinterContributor {
    override fun PrinterContext.process(module: Module) = with(printer) {
        val tasks = runReadAction { module.externalSystemTestRunTasks() }
        if (tasks.isEmpty()) return

        indented {
            tasks.forEach { println(it.toStringRepresentation()) }
        }
    }
}
