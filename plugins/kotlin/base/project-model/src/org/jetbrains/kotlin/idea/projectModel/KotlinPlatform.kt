// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

enum class KotlinPlatform(val id: String) {
    COMMON("common"), // this platform is left only for compatibility with NMPP (should not be used in HMPP)
    JVM("jvm"),
    JS("js"),
    WASM("wasm"),
    NATIVE("native"),
    ANDROID("androidJvm");

    companion object {
        fun byId(id: String) = values().firstOrNull { it.id == id }
    }
}