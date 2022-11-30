// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.module.Module

interface ModulePrinterContributor {
    fun PrinterContext.process(module: Module)
}

class NoopModulePrinterContributor : ModulePrinterContributor {
    override fun PrinterContext.process(module: Module) = Unit
}
