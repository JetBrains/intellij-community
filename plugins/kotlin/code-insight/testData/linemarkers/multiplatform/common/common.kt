// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

expect class <!LINE_MARKER("descr='Has actuals in jvm, js modules'")!>Sample<!>() {
    fun <!LINE_MARKER("descr='Has actuals in jvm, js modules'")!>checkMe<!>(): Int
}

expect object <!LINE_MARKER("descr='Has actuals in jvm, js modules'")!>Platform<!> {
    val <!LINE_MARKER("descr='Has actuals in jvm, js modules'")!>name<!>: String
}

fun hello(): String = "Hello from ${Platform.name}"
