// AFTER-WARNING: Parameter 'i' is never used
fun test(i: Int) {
    if (i == 1) {
        println(1)
    } else if (i == 2) <caret>println(2) else {
        println(3)
    }
}

fun println(i: Int) {}