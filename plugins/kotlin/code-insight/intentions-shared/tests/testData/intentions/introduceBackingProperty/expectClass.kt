// IS_APPLICABLE: false
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup

expect class Outer {
    class Nested {
        val <caret>x: Int
    }
}