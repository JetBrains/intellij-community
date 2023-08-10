// "Add parameter to constructor 'Foo'" "false"
// DISABLE-ERRORS
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Create function 'Foo'
// ACTION: Create secondary constructor
// ACTION: Put arguments on separate lines
// ACTION: Remove argument
inline class Foo(val i: Int)

val foo = Foo(10, 20<caret>)