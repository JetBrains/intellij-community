// "Create class 'Foo'" "false"
// ACTION: Add parameter to constructor 'Foo'
// ACTION: Convert to 'buildString' call
// ACTION: Convert to raw string literal
// ACTION: Create function 'Foo'
// ACTION: Create secondary constructor
// ACTION: Put arguments on separate lines
// ACTION: Remove argument
// ERROR: Too many arguments for public constructor Foo(a: Int) defined in Foo

class Foo(a: Int)

fun test() {
    val a = Foo(2, <caret>"2")
}
