// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry

abstract class AbstractKotlinReceiverUsageHighlightingTest : AbstractCustomHighlightUsageHandlerTest() {
    override fun setUp() {
        super.setUp()
        val registryValue = Registry.get(KotlinHighlightReceiverUsagesHandlerFactory.REGISTRY_FLAG)
        val currentValue = registryValue.asBoolean()
        if (currentValue) return

        registryValue.setValue(true)
        disposeOnTearDown(Disposable {
            registryValue.setValue(false)
        })
    }
}
