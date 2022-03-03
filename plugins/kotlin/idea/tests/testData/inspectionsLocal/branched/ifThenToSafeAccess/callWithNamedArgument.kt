// WITH_STDLIB
data class D(val x: Int = 1, val y: Int = 2, val z: Int = 3)

fun test(i: Int?, j: Int) {
    val x = <caret>if (i != null) D(y = i, z = j) else null
}