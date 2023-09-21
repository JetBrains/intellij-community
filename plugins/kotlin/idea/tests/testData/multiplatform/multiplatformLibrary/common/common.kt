package sample

expect class <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm; icon=nodes/Module.svg); (text=js; icon=nodes/Module.svg)]")!>Sample<!>() {
    fun <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm; icon=nodes/Module.svg); (text=js; icon=nodes/Module.svg)]")!>checkMe<!>(): Int
}

expect object <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm; icon=nodes/Module.svg); (text=js; icon=nodes/Module.svg)]")!>Platform<!> {
    val <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm; icon=nodes/Module.svg); (text=js; icon=nodes/Module.svg)]")!>name<!>: String
}

fun hello(): String = "Hello from ${Platform.name}"
