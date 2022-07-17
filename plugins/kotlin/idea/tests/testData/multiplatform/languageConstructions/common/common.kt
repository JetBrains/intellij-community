package sample

expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>commonFun<!>()
    val <!LINE_MARKER("descr='Has actuals in jvm module'")!>x<!>: Int
    val <!LINE_MARKER("descr='Has actuals in jvm module'")!>y<!>: Double
    val <!LINE_MARKER("descr='Has actuals in jvm module'")!>z<!>: String
}

fun getCommonA(): A = null!!
