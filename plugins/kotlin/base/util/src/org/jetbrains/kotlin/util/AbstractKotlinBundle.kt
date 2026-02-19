// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.util

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

abstract class AbstractKotlinBundle protected constructor(pathToBundle: String) {
    @Nls
    protected fun String.withHtml(): String = "<html>$this</html>"

    protected val instance: DynamicBundle = DynamicBundle(javaClass, pathToBundle)

    @Nls
    protected fun getMessage(key: @NonNls String, vararg params: Any?): String {
        return instance.getMessage(key, *params)
    }
}