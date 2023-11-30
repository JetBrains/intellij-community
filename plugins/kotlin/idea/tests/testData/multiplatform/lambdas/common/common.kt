@file:Suppress("UNUSED_PARAMETER")

package sample

expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>A<!>

fun useA(block: A.() -> Unit) {}

fun anotherUseA(block: (A) -> Unit) {}
