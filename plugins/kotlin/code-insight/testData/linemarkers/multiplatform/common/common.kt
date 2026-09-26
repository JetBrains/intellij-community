// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

expect class <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is subclassed by Sample [jvm] (sample) Sample [js] (sample) Press ... to navigate'")!>Sample<!>() {
    fun <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is implemented in Sample (sample) Sample (sample) Press ... to navigate'")!>checkMe<!>(): Int
}

expect object <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>Platform<!> {
    val <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>name<!>: String
}

fun hello(): String = "Hello from ${Platform.name}"

expect fun <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>foo<!>()

expect annotation class <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is subclassed by Preview [js] (sample) Preview (sample) Press ... to navigate'")!>Preview<!>()

expect object <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>SomeObject<!>()

interface GoToCommonMainInterface

expect interface <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is implemented by WithCompanion [jvm] (sample) WithCompanion [js] (sample) Press ... to navigate'")!>WithCompanion<!> {
    companion <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]")!>object<!> {}
}

expect open class <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is subclassed by BaseClass [jvm] (sample) BaseClass [js] (sample) Press ... to navigate'")!>BaseClass<!> {
    open fun  <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is implemented in BaseClass (sample) BaseClass (sample) Press ... to navigate'")!>baseMethod<!>() {}
}
