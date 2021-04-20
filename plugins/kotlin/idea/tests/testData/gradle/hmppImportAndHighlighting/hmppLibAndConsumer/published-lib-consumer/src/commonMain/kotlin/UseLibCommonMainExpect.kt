package com.h0tk3y.hmpp.klib.demo.app

import com.h0tk3y.hmpp.klib.demo.*

fun useExpect(e: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: LibCommonMainExpect'")!>LibCommonMainExpect<!>) {
    println(<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Resolved to error element'")!>e<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>libCommonMainExpectFun<!>())
//    println(e.additionalFunInActual()) // won't resolve
}
