package sample

expect class <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm); (text=js)]")!>Sample<!>() {
    fun <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm); (text=js)]")!>checkMe<!>(): Int
}

expect object <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm); (text=js)]")!>Platform<!> {
    val <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm); (text=js)]")!>name<!>: String
}

fun hello(): String = "Hello from ${Platform.name}"
