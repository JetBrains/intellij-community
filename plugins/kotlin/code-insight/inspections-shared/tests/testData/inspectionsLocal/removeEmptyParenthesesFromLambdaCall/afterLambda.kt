// PROBLEM: none

fun returnFun(fn: () -> Unit): (() -> Unit) -> Unit = {}

fun test() {
    returnFun {} ()<caret> {}
}