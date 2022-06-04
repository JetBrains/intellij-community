package sample

expect class <!LINE_MARKER("descr='Has actuals in [jvm, js] module'")!>Sample<!>() {
    fun <!LINE_MARKER("descr='Has actuals in [jvm, js] module'")!>checkMe<!>(): Int
}

expect object <!LINE_MARKER("descr='Has actuals in [jvm, js] module'")!>Platform<!> {
    val <!LINE_MARKER("descr='Has actuals in [jvm, js] module'")!>name<!>: String
}

fun hello(): String = "Hello from ${Platform.name}"
