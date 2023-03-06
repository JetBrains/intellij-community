package com.h0tk3y.hmpp.klib.demo

import kotlin.test.Test

class LibIosTest {
    @Test
    fun testLibActual() {
        LibCommonMainExpect().libCommonMainExpectFun()
        LibCommonMainExpect().additionalFunInIosActual()
        println(kotlinx.cinterop.CArrayPointer::class)
    }
}
