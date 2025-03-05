// "Add default constructor to expect class" "false"
// ENABLE_MULTIPLATFORM
// ACTION: Make internal
// ACTION: Make private
// ERROR: Expected annotation class 'Foo' has no actual declaration in module light_idea_test_case for JVM
// ERROR: This class does not have a constructor
// K2_AFTER_ERROR: Unresolved reference 'Foo'.
expect annotation class Foo

@Foo("")<caret>
fun bar() {}