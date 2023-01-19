// FIR_COMPARISON
fun foo(param: Boolean) {}

fun foo(paramNullable: Boolean?) {}

fun test() {
    val paramVariableNullable: Boolean? = null
    val paramVariable = true
    foo(para<caret>)
}

// ORDER: paramVariableNullable
// ORDER: paramVariable
// ORDER: param =
// ORDER: param = false
// ORDER: param = true
// ORDER: paramNullable =
// ORDER: paramNullable = false
// ORDER: paramNullable = null
// ORDER: paramNullable = true