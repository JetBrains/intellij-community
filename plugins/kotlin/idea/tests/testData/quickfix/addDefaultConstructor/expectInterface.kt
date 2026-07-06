// "Add default constructor to 'expect' class" "false"
// ENABLE_MULTIPLATFORM
// ACTION: Create subclass
// ACTION: Introduce import alias
// ACTION: Remove constructor call
// ERROR: This class does not have a constructor
// K2_AFTER_ERROR: ACTUAL_WITHOUT_EXPECT
// K2_AFTER_ERROR: EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE
// K2_AFTER_ERROR: EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE
// K2_AFTER_ERROR: NO_CONSTRUCTOR
// K2_ERROR: ACTUAL_WITHOUT_EXPECT
// K2_ERROR: EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE
// K2_ERROR: EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE
// K2_ERROR: NO_CONSTRUCTOR

expect interface A

open class C : A<caret>()

actual interface A