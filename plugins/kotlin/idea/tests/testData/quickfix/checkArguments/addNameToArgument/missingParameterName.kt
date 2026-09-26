// "Add name to argument" "false"

fun foo(
    : Int, // missing parameter name
) {}

fun test() {
    foo(<caret>42)
}
