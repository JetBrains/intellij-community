// PROBLEM: none
// K2_ERROR: CONTEXT_PARAMETER_WITHOUT_NAME

context(Int)
fun other() {

}

fun Int<caret>.test() {
    other()
}