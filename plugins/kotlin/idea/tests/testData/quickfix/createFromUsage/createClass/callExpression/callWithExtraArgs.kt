// "Create class 'Foo'" "false"
// ERROR: Too many arguments for public constructor Foo(a: Int) defined in Foo
// K2_AFTER_ERROR: TOO_MANY_ARGUMENTS
// K2_ERROR: TOO_MANY_ARGUMENTS

class Foo(a: Int)

fun test() {
    val a = Foo(2, <caret>"2")
}
