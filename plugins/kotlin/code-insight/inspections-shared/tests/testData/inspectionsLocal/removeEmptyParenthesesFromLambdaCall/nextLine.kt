// PROBLEM: none

fun foo(f: (Int) -> Unit) = f(4)

fun bar() {
    foo()<caret>
    {

    }
}