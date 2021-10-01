@file:Suppress("UNUSED_PARAMETER")

package sample

// --------------------------------------------

expect interface <!LINE_MARKER("descr='Is subclassed by B  Click or press ... to navigate'"), LINE_MARKER("descr='Has actuals in JVM (2 modules)'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in JVM (2 modules)'")!>foo<!>()
}

fun take_A_common_1(x: A) {
    x.foo()
}
