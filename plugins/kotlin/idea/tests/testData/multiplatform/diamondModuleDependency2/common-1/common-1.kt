@file:Suppress("UNUSED_PARAMETER")
package sample

interface AA
interface BB
interface CC
interface DD

expect interface <!LINE_MARKER("descr='Has actuals in common-2-3 module'"), LINE_MARKER("descr='Is subclassed by B [common-2-1] (sample) C [common-2-2] (sample) D (sample) Press ... to navigate'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in common-2-3 module'")!>foo_A<!>()
}

fun take0(x: A): AA = null!!
fun take1(x: A): AA = null!!
fun take2(x: A): AA = null!!
fun take3(x: A): AA = null!!
fun take4(x: A): AA = null!!

fun test(x: A) {
    take4(x)
}
