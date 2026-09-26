fun foo(f: () -> Unit) = Unit

fun bar() {
    foo {<caret>
}