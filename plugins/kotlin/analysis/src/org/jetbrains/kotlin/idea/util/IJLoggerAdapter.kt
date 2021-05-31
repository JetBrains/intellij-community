// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.util.Logger as KLogger
import com.intellij.openapi.diagnostic.Logger as IJLogger

class IJLoggerAdapter private constructor(private val logger: IJLogger) : KLogger {
    override fun log(message: String) = logger.info(message)
    override fun warning(message: String) = logger.warn(message)

    override fun error(message: String) = fatal(message)
    override fun fatal(message: String): Nothing {
        logger.error(message)
        error(message) // normally, this line of code should not be reached
    }

    companion object {
        fun getInstance(clazz: Class<*>): KLogger = IJLoggerAdapter(IJLogger.getInstance(clazz))
    }
}
