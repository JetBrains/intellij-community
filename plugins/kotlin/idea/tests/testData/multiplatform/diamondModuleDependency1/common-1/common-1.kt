@file:Suppress("UNUSED_PARAMETER")

package sample

// --------------------------------------------

expect interface <!LINE_MARKER("descr='Has actuals in [common-2-2, common-2-1] module'"), LINE_MARKER("descr='Is subclassed by B  Click or press ... to navigate'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in [common-2-2, common-2-1] module'")!>foo<!>()
}

fun take_A_common_1(x: A) {
    x.foo()
}
