// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
// SKIP_ERRORS_AFTER
// SKIP_WARNINGS_AFTER
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun main(args: Array<String>) {
    val x = "x"
    val y = $$"$${x.length}<caret>"

    println(y) //ouput is 1

}