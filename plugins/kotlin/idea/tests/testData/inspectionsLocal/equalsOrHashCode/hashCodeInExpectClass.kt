// AFTER_ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
// K2_AFTER_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION

expect class With<caret>Constructor(x: Int, s: String) {
    val x: Int
    val s: String

    override fun equals(other: Any?): Boolean
}
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup