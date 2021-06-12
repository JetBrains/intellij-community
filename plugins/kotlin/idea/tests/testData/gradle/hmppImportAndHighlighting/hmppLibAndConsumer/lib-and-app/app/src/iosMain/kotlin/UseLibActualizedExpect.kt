package com.h0tk3y.hmpp.klib.demo.app

import com.h0tk3y.hmpp.klib.demo.*
import kotlinx.cinterop.CArrayPointer

fun f() {
    LibCommonMainExpect().additionalFunInIosActual()
    useExpect(LibCommonMainExpect())
    libCommonMainTopLevelFun()
    println(CArrayPointer::class)
}