// "Add default constructor to expect class" "false"
// ENABLE_MULTIPLATFORM
// ACTION: Make internal
// ACTION: Make private
// ERROR: Expected annotation class 'Foo' has no actual declaration in module light_idea_test_case for JVM
// ERROR: This class does not have a constructor
// K2_AFTER_ERROR: NO_IMPLICIT_DEFAULT_CONSTRUCTOR_ON_EXPECT_CLASS
// K2_ERROR: NO_IMPLICIT_DEFAULT_CONSTRUCTOR_ON_EXPECT_CLASS
expect annotation class Foo

@Foo("")<caret>
fun bar() {}