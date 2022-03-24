@file:Suppress("UNUSED_PARAMETER")
package sample

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'")!>C<!> : A {
    fun foo_C_1()
}

fun take1(x: C): CC = null!!

fun getC(): C = null!!
