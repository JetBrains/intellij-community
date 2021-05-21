// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.DynamicBundle
import com.intellij.openapi.util.NlsSafe

abstract class AbstractKotlinBundleIndependent protected constructor(pathToBundle: String) : DynamicBundle(pathToBundle) {
    @NlsSafe
    protected fun String.withHtml(): String = "<html>$this</html>"
}