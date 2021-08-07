package com.h0tk3y.hmpp.klib.demo

import kotlin.test.Test

class <!LINE_MARKER{OSX}("descr='Run Test'")!>LibIosTest<!> {
    @Test
    fun <!LINE_MARKER{OSX}("descr='Run Test'")!>testLibActual<!>() {
        LibCommonMainExpect().libCommonMainExpectFun()
        LibCommonMainExpect().additionalFunInIosActual()
        println(kotlinx.cinterop.CArrayPointer::class)
    }
}