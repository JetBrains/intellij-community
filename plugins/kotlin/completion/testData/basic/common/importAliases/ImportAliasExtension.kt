import fooFun as barFun

private fun Int.fooFun() {

}

fun test() {
    5.barFu<caret>
}

// EXIST: barFun
// IGNORE_K2