// "Replace with 'newFun(p)'" "true"
// WITH_STDLIB

@Deprecated("", ReplaceWith("newFun(p)"))
fun oldFun(vararg p: Boolean){
    newFun(p)
}

fun newFun(p: BooleanArray){}

fun foo() {
    <caret>oldFun(true, false)
}
