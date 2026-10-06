// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// IS_APPLICABLE: false
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION

actual class Foo {
    actual fun ba<caret>r() {}
}
