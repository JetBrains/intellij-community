interface Foo

fun foo(f: Foo, i: Int){}

fun bar() {
    foo(<caret>)
}

//ELEMENT: object

// IGNORE_K2
