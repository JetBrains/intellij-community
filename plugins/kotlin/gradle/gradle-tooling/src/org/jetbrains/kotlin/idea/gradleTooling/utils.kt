// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import java.util.*

fun Class<*>.getMethodOrNull(name: String, vararg parameterTypes: Class<*>) =
    try {
        getMethod(name, *parameterTypes)
    } catch (e: Exception) {
        null
    }

fun Class<*>.getDeclaredMethodOrNull(name: String, vararg parameterTypes: Class<*>) =
    try {
        getDeclaredMethod(name, *parameterTypes)?.also { it.isAccessible = true }
    } catch (e: Exception) {
        null
    }

fun ClassLoader.loadClassOrNull(name: String): Class<*>? {
    return try {
        loadClass(name)
    } catch (e: LinkageError) {
        return null
    } catch (e: ClassNotFoundException) {
        return null
    }
}

fun compilationFullName(simpleName: String, classifier: String?) =
    if (classifier != null) classifier + simpleName.capitalize() else simpleName

fun String.capitalize(): String {
    /* Default implementation as suggested by 'capitalize' deprecation */
    if (KotlinVersion.CURRENT.isAtLeast(1, 5)) {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    /* Fallback implementation for older Kotlin versions */
    if (this.isEmpty()) return this
    @Suppress("DEPRECATION")
    return this[0].toUpperCase() + this.drop(1)
}
