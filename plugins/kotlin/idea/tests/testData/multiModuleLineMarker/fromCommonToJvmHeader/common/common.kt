expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>Header<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>foo<!>(): Int
}

expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>Incomplete<!> {
    fun foo(): Int
}

expect fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>foo<!>(arg: Int): String

expect val <!LINE_MARKER("descr='Has actuals in jvm module'")!>flag<!>: Boolean
