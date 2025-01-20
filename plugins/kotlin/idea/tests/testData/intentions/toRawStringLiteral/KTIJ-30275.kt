// IS_APPLICABLE: false
// IGNORE_K1
// K2_ERROR: The feature "multi dollar interpolation" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xmulti-dollar-interpolation', but note that no stability guarantees are provided.

fun main(args: Array<String>) {
    val x = "x"
    val y = $$"$${x.<caret>length}"

    println(y) //ouput is 1

}