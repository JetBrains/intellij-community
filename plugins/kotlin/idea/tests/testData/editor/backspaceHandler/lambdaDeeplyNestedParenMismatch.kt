fun foo(f: () -> Unit) = Unit

fun bar() {
    foo(foo { foo { foo {<caret> } } })
}
