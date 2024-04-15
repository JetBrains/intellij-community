package sample

expect class <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js); (text=jvm)]")!>Sample<!>() {
    fun <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js); (text=jvm)]")!>checkMe<!>(): Int
}

expect object <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js); (text=jvm)]")!>Platform<!> {
    val <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js); (text=jvm)]")!>name<!>: String
}

fun hello(): String = "Hello from ${Platform.name}"
