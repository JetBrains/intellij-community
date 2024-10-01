// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.util

import com.intellij.openapi.components.ComponentManager
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

inline fun <reified T : Any> ComponentManager.k1Service(): T {
    val serviceClass = T::class.java
    getService(serviceClass)?.let { return it }

    val name = serviceClass.name
    throw if (KotlinPluginModeProvider.isK2Mode()) {
        IllegalStateException("$name should not be used for the K2 mode. See https://kotl.in/analysis-api/ for the migration.")
    } else {
        IllegalStateException(
            "Cannot find service $name (" +
                    "classloader=${serviceClass.classLoader}, " +
                    "serviceContainer=$this, " +
                    "serviceContainerClass=${this::class.java.name}" +
                    ")"
        )
    }
}
