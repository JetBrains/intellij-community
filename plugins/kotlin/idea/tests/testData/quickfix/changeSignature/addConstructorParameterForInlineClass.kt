// "Add parameter to constructor 'Foo'" "false"
// DISABLE_ERRORS
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Create function 'Foo'
// ACTION: Create secondary constructor
// ACTION: Put arguments on separate lines
// ACTION: Remove argument
inline class Foo(val i: Int)

val foo = Foo(10, 20<caret>)

// IGNORE_K2
// Note: K2 does not support all of inspections, intentions, and quickfixes yet. When enabling K2,
// this test fails because the last two actions are missing at this moment. After implementing all
// inspections/intentions/quickfixes based on K2, we should remove the above "IGNORE_K2" directive.