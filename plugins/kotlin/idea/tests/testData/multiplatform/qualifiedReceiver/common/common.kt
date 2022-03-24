@file:Suppress("NO_ACTUAL_FOR_EXPECT")

package foo

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>commonFun<!>()
    val <!LINE_MARKER("descr='Has actuals in jvm module'")!>b<!>: B
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>bFun<!>(): B
}

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'")!>B<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>commonFunB<!>()
}

class Common {
    val a: A get() = null!!
    fun aFun(): A = null!!
}
