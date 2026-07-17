// PROBLEM: none
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
// K2_ERROR: EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE
// K2_ERROR: EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE
// K2_ERROR: EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE
// K2_ERROR: EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION

//common
expect fun op(expectParameter: String)

//platform
actual fun op(actualParameter: String) {}


//common
expect class CtrParams23(expectClass: String) {}

//platform
actual class CtrParams23 actual constructor(actual<caret>Class: String) {}

