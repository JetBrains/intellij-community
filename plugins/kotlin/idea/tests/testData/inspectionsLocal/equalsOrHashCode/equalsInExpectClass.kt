// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup

expect class With<caret>Constructor(x: Int, s: String) {
    val x: Int
    val s: String

    override fun hashCode(): Int
}