package test

actual class <!LINE_MARKER("descr='Has expects in commonMpp module'")!>Expect<!> {
    actual fun <!LINE_MARKER("descr='Has expects in commonMpp module'")!>commonFun<!>(): String = ""

    fun platformFun(): Int = 42
}

fun topLevelPlatformFun(): String = ""
