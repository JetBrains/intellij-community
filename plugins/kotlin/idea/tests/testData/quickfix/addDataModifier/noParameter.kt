// "Make 'Foo' data class" "false"
// ACTION: Create extension function 'Foo.component1'
// ACTION: Create extension function 'Foo.component2'
// ACTION: Create member function 'Foo.component1'
// ACTION: Create member function 'Foo.component2'
// ACTION: Enable a trailing comma by default in the formatter
// ERROR: Destructuring declaration initializer of type Foo must have a 'component1()' function
// ERROR: Destructuring declaration initializer of type Foo must have a 'component2()' function
// K2_AFTER_ERROR: Destructuring of type 'Foo' requires operator function 'component1()'.
// K2_AFTER_ERROR: Destructuring of type 'Foo' requires operator function 'component2()'.
class Foo()

fun test() {
    var (bar, baz) = Foo()<caret>
}