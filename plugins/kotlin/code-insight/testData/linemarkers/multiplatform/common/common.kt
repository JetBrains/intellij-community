// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

expect class <!LINE_MARKER("descr='Has actuals in js, jvm modules'; targets=[(text=jvm); (text=js)]")!>Sample<!>() {
    fun <!LINE_MARKER("descr='Has actuals in js, jvm modules'; targets=[(text=jvm); (text=js)]")!>checkMe<!>(): Int
}

expect object <!LINE_MARKER("descr='Has actuals in js, jvm modules'; targets=[(text=jvm); (text=js)]")!>Platform<!> {
    val <!LINE_MARKER("descr='Has actuals in js, jvm modules'; targets=[(text=jvm); (text=js)]")!>name<!>: String
}

fun hello(): String = "Hello from ${Platform.name}"

expect fun <!LINE_MARKER("descr='Has actuals in js, jvm modules'; targets=[(text=jvm); (text=js)]")!>foo<!>()

expect annotation class <!LINE_MARKER("descr='Has actuals in js, jvm modules'; targets=[(text=js); (text=jvm)]")!>Preview<!>()

expect object <!LINE_MARKER("descr='Has actuals in js, jvm modules'; targets=[(text=js); (text=jvm)]")!>SomeObject<!>()
