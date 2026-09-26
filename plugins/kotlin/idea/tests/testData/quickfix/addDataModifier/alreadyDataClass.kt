// "Make 'Foo' data class" "false"
// ERROR: Destructuring declaration initializer of type Pair must have a 'component3()' function
// K2_AFTER_ERROR: COMPONENT_FUNCTION_MISSING
// K2_ERROR: COMPONENT_FUNCTION_MISSING

data class Pair(val first: Int, val second: Int)

fun foo(pairs: List<Pair>) {
    for ((_, _, _) in pa<caret>irs) {}
}
