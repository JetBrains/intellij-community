@file:Suppress("UNUSED_PARAMETER")

package sample

actual interface <!LINE_MARKER("descr='Has expects in common-1 module'"), LINE_MARKER("descr='Is implemented by B (sample) Press ... to navigate'")!>A<!> {
    actual fun <!LINE_MARKER("descr='Has expects in common-1 module'")!>foo<!>()
    fun bar()
}

fun take_A_common_2_1(x: A) {
    x.foo()
    x.bar()
}
