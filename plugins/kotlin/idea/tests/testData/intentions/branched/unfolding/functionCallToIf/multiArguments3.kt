fun test(b: Boolean) {
    <caret>println(x = 1, y = if (b) 2 else 4, z = 3)
}

fun println(x: Int, y: Int, z: Int) {}