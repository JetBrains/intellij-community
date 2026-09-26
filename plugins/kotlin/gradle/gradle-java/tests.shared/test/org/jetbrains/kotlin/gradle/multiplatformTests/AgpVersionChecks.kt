// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

fun String?.isAgp9OrHigher(): Boolean {
    return (this?.substringBefore('.')?.toIntOrNull() ?: 0) >= 9
}

fun TestVersion<String>?.isAgp9OrHigher(): Boolean {
    return this?.version.isAgp9OrHigher()
}
