// "class org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix" "false"
// ERROR: Too many arguments for public open operator fun equals(other: Any?): Boolean defined in kotlin.Any
// K2_AFTER_ERROR: Too many arguments for 'fun equals(other: Any?): Boolean'.

fun f(d: Any) {
    d.equals("a", <caret>"b")
}
