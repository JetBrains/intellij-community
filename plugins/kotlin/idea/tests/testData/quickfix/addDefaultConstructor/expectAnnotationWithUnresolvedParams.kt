// "Add default constructor to 'expect' class" "false"
// ENABLE_MULTIPLATFORM
// ERROR: Expected annotation class 'Foo' has no actual declaration in module light_idea_test_case for JVM
// ERROR: This class does not have a constructor
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: y
// ERROR: Unresolved reference: z
// K2_AFTER_ERROR: Unresolved reference 'Foo'.
// K2_AFTER_ERROR: Unresolved reference 'x'.
// K2_AFTER_ERROR: Unresolved reference 'y'.
// K2_AFTER_ERROR: Unresolved reference 'z'.

expect annotation class Foo

@Foo(x, y<caret>, z)
fun bar() {}