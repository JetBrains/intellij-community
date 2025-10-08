// "Rename variable 'a' to explicitly ignore return value" "true"
// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
fun foo() = 42
fun f() {
    val <caret>a = foo()
}
