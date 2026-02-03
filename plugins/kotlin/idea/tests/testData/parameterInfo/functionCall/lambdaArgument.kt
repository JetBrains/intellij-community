fun test() {
    foo {<caret> it.length }
}

fun foo(f: (String) -> Int) {}
