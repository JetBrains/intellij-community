// this test tests that no space auto-inserted after comma if we are just typing

fun foo(p1: Int, p2: Int) { }

fun bar(ppp: Int, ppp1: Int) {
    foo(ppp<caret>)
}

// INVOCATION_COUNT: 0
// ELEMENT: *
// CHAR: ','
