// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LoggingUtils")
package org.jetbrains.kotlin.idea.base.util

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.util.Logger as KotlinLogger

fun Logger.asKotlinLogger(): KotlinLogger {
    return object : KotlinLogger {
        override fun log(message: String) = this@asKotlinLogger.info(message)
        override fun warning(message: String) = this@asKotlinLogger.warn(message)

        override fun error(message: String) = fatal(message)
        override fun fatal(message: String): Nothing {
            this@asKotlinLogger.error(message)
            error(message) // Normally, this line of code should not be reached
        }
    }
}