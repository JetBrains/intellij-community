import fooFun as barFun

private fun fooFun() {}

fun test() {
    barFu<caret>
}

// EXIST: barFun
// IGNORE_K2