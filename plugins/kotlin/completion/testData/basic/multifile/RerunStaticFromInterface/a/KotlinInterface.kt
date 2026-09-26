// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package a

interface KotlinInterface {
    companion object {
        const val somePrefixKotlinStaticField = "staticField"
        fun somePrefixKotlinStaticMethod() = "staticMethod"
    }

    fun somePrefixKotlinInstanceMethod() = "instanceMethod"
}

// ALLOW_AST_ACCESS
