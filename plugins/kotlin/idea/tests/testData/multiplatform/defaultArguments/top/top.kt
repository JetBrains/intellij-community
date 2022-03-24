package sample

import sample.A.Companion.companionExtensionFunction

expect class <!LINE_MARKER("descr='Has actuals in middle module'")!>A<!>() {
    fun <!LINE_MARKER("descr='Has actuals in middle module'")!>memberFunction<!>(x: Int, y: String = "ok")
    companion <!LINE_MARKER("descr='Has actuals in middle module'")!>object<!> {
        fun <!LINE_MARKER("descr='Has actuals in middle module'")!>companionFunction<!>(x: Int, y: String = "ok")
        fun String.<!LINE_MARKER("descr='Has actuals in middle module'")!>companionExtensionFunction<!>(x: Int, y: String = "ok")
    }
}

expect fun <!LINE_MARKER("descr='Has actuals in middle module'")!>topLevelFunction<!>(x: Int, y: String = "ok")

expect fun String.<!LINE_MARKER("descr='Has actuals in middle module'")!>topLevelExtensionFunction<!>(x: Int, y: String = "ok")

fun test() {
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

fun A.test() {
    "".companionExtensionFunction(<!NO_VALUE_FOR_PARAMETER!>)<!>
    "".companionExtensionFunction(42)
    "".companionExtensionFunction(42, "ok")
}
