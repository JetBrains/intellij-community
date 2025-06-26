// PROBLEM: none
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
// K2_ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup

expect class DatabaseConfiguration {
    fun implicitlyExpectFun(k<caret>ey: String, value: String?)
}