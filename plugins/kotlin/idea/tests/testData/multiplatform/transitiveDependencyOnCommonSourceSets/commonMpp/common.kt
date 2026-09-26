package test

expect class <!LINE_MARKER("descr='Has actuals in jvmMpp module'")!>Expect<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvmMpp module'")!>commonFun<!>(): String
}

fun topLevelCommonFun(): Int = 42
