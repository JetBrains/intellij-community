@file:Suppress("UNUSED_PARAMETER")

package sample

// --------------------------------------------

expect interface <!LINE_MARKER("descr='Has actuals in [common-2-2, common-2-1] modules'"), LINE_MARKER("descr='Is subclassed by B (sample) Press ... to navigate'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in [common-2-2, common-2-1] modules'")!>foo<!>()
}

fun take_A_common_1(x: A) {
    x.foo()
}
