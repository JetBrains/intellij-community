class C{}

fun foo(c: C){}
fun foo(c: C?, i: Int){}

fun foo() {
    foo(<caret>
}

// ELEMENT: C

// IGNORE_K2
