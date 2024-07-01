// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

expect class <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>Sample<!>() {
    fun <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>checkMe<!>(): Int
}

expect object <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>Platform<!> {
    val <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>name<!>: String
}

fun hello(): String = "Hello from ${Platform.name}"

expect fun <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>foo<!>()

expect annotation class <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>Preview<!>()

expect object <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>SomeObject<!>()

interface GoToCommonMainInterface

expect interface <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>WithCompanion<!> {
    companion <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>object<!> {}
}