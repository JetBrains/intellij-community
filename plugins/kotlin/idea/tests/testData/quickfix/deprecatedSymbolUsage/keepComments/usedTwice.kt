// "Replace with 'newFun(p, p)'" "true"

@Deprecated("", ReplaceWith("newFun(p, p)"))
fun oldFun(p: Int) {
    newFun(p, p)
}

fun newFun(p1: Int, p2: Int){}

fun foo() {
    <caret>oldFun(0/*use zero*/)
}
