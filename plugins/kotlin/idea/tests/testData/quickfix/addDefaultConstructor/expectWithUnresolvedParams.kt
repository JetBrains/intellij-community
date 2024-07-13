// "Add default constructor to 'expect' class" "false"
// ENABLE_MULTIPLATFORM
// ERROR: Expected class 'A' has no actual declaration in module light_idea_test_case for JVM
// ERROR: This class does not have a constructor
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: y
// ERROR: Unresolved reference: z

expect open class A

open class C : A(x, y<caret>, z)