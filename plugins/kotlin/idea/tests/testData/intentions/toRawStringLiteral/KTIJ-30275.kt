// IS_APPLICABLE: false
// IGNORE_K1

fun main(args: Array<String>) {
    val x = "x"
    val y = $$"$${x.<caret>length}"

    println(y) //ouput is 1

}