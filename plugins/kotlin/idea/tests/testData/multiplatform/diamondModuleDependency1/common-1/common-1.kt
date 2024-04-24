@file:Suppress("UNUSED_PARAMETER")

package sample

// --------------------------------------------

expect interface <!LINE_MARKER("descr='Has actuals in [common-2-1, common-2-2] modules'; targets=[(text=common-2-1); (text=common-2-2)]"), LINE_MARKER("descr='Is subclassed by B (sample) Press ... to navigate'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in [common-2-1, common-2-2] modules'; targets=[(text=common-2-1); (text=common-2-2)]")!>foo<!>()
}

fun take_A_common_1(x: A) {
    x.foo()
}
