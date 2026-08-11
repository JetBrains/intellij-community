// IS_APPLICABLE: false
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION

expect class Outer {
    class Nested {
        val <caret>x: Int
    }
}