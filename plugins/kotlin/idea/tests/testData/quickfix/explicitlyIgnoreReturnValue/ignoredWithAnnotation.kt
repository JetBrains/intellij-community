// "Explicitly ignore return value" "false"
// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
fun someFunction() {
    <caret>someFunctionValue()
}
@IgnorableReturnValue
fun someFunctionValue(): Int = 1

// IGNORE_K1