// PROBLEM: none
fun test(int: Int?): Unit = if (int == 5) {
    Unit<caret>
} else {
    foo()
}

fun foo() {}
