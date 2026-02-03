// PROBLEM: none
fun test(int: Int?): Unit = if (int == 5) {
    foo()
} else {
    Unit<caret>
}

fun foo() {}
