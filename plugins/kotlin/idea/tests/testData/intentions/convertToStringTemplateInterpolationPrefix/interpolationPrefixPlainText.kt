// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// ISSUE: KTIJ-30491

fun main(args: Array<String>) {
    val name = "Alice"
    val welcomeMessage = <caret>$$"Hi, " + name + "!"
}