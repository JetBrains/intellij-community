@file:Suppress("ACTUAL_WITHOUT_EXPECT")

package aliases

actual class <!LINE_MARKER("descr='Has expects in common module'")!>A<!> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>commonFun<!>() {}
    fun platformFun() {}
}

typealias A2 = A1
typealias A3 = A

actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>B<!> = A

typealias B2 = B
typealias B3 = B1

class PlatformInv<T>(val value: T)
