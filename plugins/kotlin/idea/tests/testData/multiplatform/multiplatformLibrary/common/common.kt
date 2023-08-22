package sample

expect class <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'")!>Sample<!>() {
    fun <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'")!>checkMe<!>(): Int
}

expect object <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'")!>Platform<!> {
    val <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'")!>name<!>: String
}

fun hello(): String = "Hello from ${Platform.name}"
