// IS_APPLICABLE: false
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION

actual class Foo {
    actual companion object {
        actual fun ba<caret>r(): Int = 1
    }
}
