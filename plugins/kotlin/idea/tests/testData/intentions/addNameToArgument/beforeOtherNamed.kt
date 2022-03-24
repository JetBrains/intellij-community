// INTENTION_TEXT: "Add 'a =' to argument"
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used

fun foo(a: Int, b: String){}

fun bar() {
    foo(<caret>1, b = "")
}