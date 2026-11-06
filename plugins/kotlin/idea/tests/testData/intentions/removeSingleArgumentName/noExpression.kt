// IS_APPLICABLE: false
// K2_ERROR: SYNTAX
fun foo(s: String, b: Boolean){}

fun bar() {
    foo("", <caret>b = )
}