// "Make 'Foo' data class" "false"
// ACTION: Create extension function 'Foo.component1'
// ACTION: Create extension function 'Foo.component2'
// ACTION: Create member function 'Foo.component1'
// ACTION: Create member function 'Foo.component2'
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Put arguments on separate lines
// ERROR: Destructuring declaration initializer of type Foo must have a 'component1()' function
// ERROR: Destructuring declaration initializer of type Foo must have a 'component2()' function
// K2_AFTER_ERROR: Destructuring of type 'Foo' requires operator function 'component1()'.
// K2_AFTER_ERROR: Destructuring of type 'Foo' requires operator function 'component2()'.
open class Foo(val bar: String, val baz: Int)

fun test() {
    var (bar, baz) = Foo("A", 1)<caret>
}