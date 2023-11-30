@file:Suppress("ACTUAL_WITHOUT_EXPECT")

package foo

actual class <!LINE_MARKER("descr='Has expects in common module'")!>A<!> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>commonFun<!>() {}
    actual val <!LINE_MARKER("descr='Has expects in common module'")!>b<!>: B = B()
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>bFun<!>(): B = B()
    fun platformFun() {}
}

actual class <!LINE_MARKER("descr='Has expects in common module'")!>B<!> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>commonFunB<!>() {}
    fun platformFunB() {}
}
