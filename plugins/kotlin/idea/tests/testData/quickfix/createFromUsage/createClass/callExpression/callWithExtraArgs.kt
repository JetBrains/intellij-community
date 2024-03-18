// "Create class 'Foo'" "false"
// ERROR: Too many arguments for public constructor Foo(a: Int) defined in Foo

class Foo(a: Int)

fun test() {
    val a = Foo(2, <caret>"2")
}
