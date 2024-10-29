// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// SKIP_ERRORS_BEFORE
fun main(args: Array<String>) {
    val name = "Alice"
    val welcomeMessage = <caret>$"Hi, " + name + "!"
}