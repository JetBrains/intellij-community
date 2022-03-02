package sample

import sample.A.Companion.companionExtensionFunction

actual class <!LINE_MARKER("descr='Has expects in top module'")!>A<!> {
    actual fun <!LINE_MARKER("descr='Has expects in top module'")!>memberFunction<!>(x: Int, y: String) = Unit
    actual companion <!LINE_MARKER("descr='Has expects in top module'")!>object<!> {
        actual fun <!LINE_MARKER("descr='Has expects in top module'")!>companionFunction<!>(x: Int, y: String) = Unit
        actual fun String.<!LINE_MARKER("descr='Has expects in top module'")!>companionExtensionFunction<!>(x: Int, y: String) = Unit
    }
}

actual fun <!LINE_MARKER("descr='Has expects in top module'")!>topLevelFunction<!>(x: Int, y: String) = Unit

actual fun String.<!LINE_MARKER("descr='Has expects in top module'")!>topLevelExtensionFunction<!>(x: Int, y: String) = Unit

fun testMiddle() {
    A().memberFunction(<!NO_VALUE_FOR_PARAMETER!>)<!>
    A().memberFunction(42)
    A().memberFunction(42, "ok")

    topLevelFunction(<!NO_VALUE_FOR_PARAMETER!>)<!>
    topLevelFunction(42)
    topLevelFunction(42, "ok")

    "".topLevelExtensionFunction(<!NO_VALUE_FOR_PARAMETER!>)<!>
    "".topLevelExtensionFunction(42)
    "".topLevelExtensionFunction(42, "ok")
}

fun A.testMiddle() {
    "".companionExtensionFunction(<!NO_VALUE_FOR_PARAMETER!>)<!>
    "".companionExtensionFunction(42)
    "".companionExtensionFunction(42, "ok")
}
