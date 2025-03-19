// "Create expected class in common module testModule_Common" "true"
// DISABLE_ERRORS
// IGNORE_K2

actual class My<caret> {
    class Middle() {
        class Inner actual constructor() {
            enum class MyEnum {
                FOO, TEST;

                actual fun check() = "42"
            }
        }
    }
}