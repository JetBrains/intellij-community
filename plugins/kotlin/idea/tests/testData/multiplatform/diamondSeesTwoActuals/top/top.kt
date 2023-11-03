package sample

expect class <!LINE_MARKER("descr='Has actuals in [left, right] modules'; targets=[(text=left); (text=right)]")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in [left, right] modules'; targets=[(text=left); (text=right)]")!>foo<!>(): Int
}
