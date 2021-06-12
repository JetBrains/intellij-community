package com.h0tk3y.hmpp.klib.demo.app

import com.h0tk3y.hmpp.klib.demo.*

fun f() {
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: LibCommonMainExpect'")!>LibCommonMainExpect<!>().<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>additionalFunInJvmAndJsActual<!>()
    useExpect(<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: LibCommonMainExpect'")!>LibCommonMainExpect<!>())
}