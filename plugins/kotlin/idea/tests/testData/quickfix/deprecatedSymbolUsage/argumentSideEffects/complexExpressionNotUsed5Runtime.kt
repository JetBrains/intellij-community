// "Replace with 'newFun(p2)'" "true"
// WITH_STDLIB

@Deprecated("", ReplaceWith("newFun(p2)"))
fun oldFun(p1: Int, p2: Int): Boolean {
    return newFun(p2)
}

fun newFun(p: Int) = false

fun foo(list: List<Int>) {
    list.filter { !<caret>oldFun(bar(), it) }
}

fun bar(): Int = 0
