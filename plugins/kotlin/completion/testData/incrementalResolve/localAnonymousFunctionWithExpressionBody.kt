fun foo() {
    <change>
    val foo = fun(result: Int): Int = re<before><caret>
}

// TYPE: ""
// COMPLETION_TYPE: BASIC
// EXIST: result
