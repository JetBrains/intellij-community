// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.DynamicBundle

abstract class AbstractKotlinIndependentBundle protected constructor(pathToBundle: String) : DynamicBundle(pathToBundle) {
    protected fun String.withHtml(): String = "<html>$this</html>"
}