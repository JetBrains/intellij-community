// FIR_COMPARISON
fun foo(param: String) {}

fun foo(param: Int) {}

fun test() {
    val paramLocalString = ""
    val paramLocalInt = 1
    foo(para<caret>)
}

// ORDER: "param ="
// ORDER: paramLocalInt
// ORDER: paramLocalString
