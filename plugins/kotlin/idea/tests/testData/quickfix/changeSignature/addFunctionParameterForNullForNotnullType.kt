// "Add 1st parameter to function 'foo'" "true"

fun foo(s : String) = Unit
fun test() {
    foo(<caret>null, "")
}