// IS_APPLICABLE: false
fun test(b: Boolean) {
    <caret>if (b) println(1, 2, 3) else println(1, 4, 5)
}

fun println(x: Int, y: Int, z: Int) {}