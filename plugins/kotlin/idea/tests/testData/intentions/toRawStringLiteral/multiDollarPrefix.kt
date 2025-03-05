// IS_APPLICABLE: false
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun main(args: Array<String>) {
    val x = "x"
    val y = $$"$${x.<caret>length}"

    println(y) //ouput is 1

}