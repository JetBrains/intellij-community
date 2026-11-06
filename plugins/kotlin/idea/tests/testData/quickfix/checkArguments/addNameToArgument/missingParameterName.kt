// "Add name to argument" "false"
// K2_ERROR: SYNTAX
// K2_AFTER_ERROR: SYNTAX

fun foo(
    : Int, // missing parameter name
) {}

fun test() {
    foo(<caret>42)
}
