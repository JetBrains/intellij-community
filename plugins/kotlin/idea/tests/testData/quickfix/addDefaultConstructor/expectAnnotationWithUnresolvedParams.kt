// "Add default constructor to 'expect' class" "false"
// ENABLE_MULTIPLATFORM
// ERROR: Expected annotation class 'Foo' has no actual declaration in module light_idea_test_case for JVM
// ERROR: This class does not have a constructor
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: y
// ERROR: Unresolved reference: z
// K2_AFTER_ERROR: NO_IMPLICIT_DEFAULT_CONSTRUCTOR_ON_EXPECT_CLASS
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: NO_IMPLICIT_DEFAULT_CONSTRUCTOR_ON_EXPECT_CLASS
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

expect annotation class Foo

@Foo(x, y<caret>, z)
fun bar() {}