@file:Suppress("NO_ACTUAL_FOR_EXPECT")

package aliases

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>commonFun<!>()
}

typealias A1 = A

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'")!>B<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>commonFun<!>()
}

typealias B1 = B

class CommonInv<T>(val value: T)
