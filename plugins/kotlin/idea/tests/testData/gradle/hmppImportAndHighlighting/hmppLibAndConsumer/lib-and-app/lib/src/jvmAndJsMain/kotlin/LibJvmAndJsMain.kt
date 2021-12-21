package com.h0tk3y.hmpp.klib.demo

actual class <!LINE_MARKER("descr='Has expects in lib-and-app.lib.commonMain module'")!>LibCommonMainExpect<!> actual constructor(): LibCommonMainInterface {
    actual fun <!LINE_MARKER("descr='Has expects in lib-and-app.lib.commonMain module'")!>libCommonMainExpectFun<!>() {
        println("actualized in jvmAndJsMain")
        libCommonMainTopLevelFun()
    }

    fun additionalFunInJvmAndJsActual() {
        println("additional fun from jvmAndJsMain")
    }
}
