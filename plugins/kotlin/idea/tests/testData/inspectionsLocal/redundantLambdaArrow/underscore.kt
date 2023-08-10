fun foo(f: (String) -> Unit) {}

fun bar() {
    foo { <caret>_ -> }
}
// PROBLEM: none