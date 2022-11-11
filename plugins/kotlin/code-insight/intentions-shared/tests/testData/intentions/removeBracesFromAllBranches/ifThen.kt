// AFTER-WARNING: Parameter 'i' is never used
fun test(i: Int) {
    if (i == 1) {
        prin<caret>tln(1)
    } else if (i == 2) {
        println(2)
    } else {
        println(3)
    }
}

fun println(i: Int) {}