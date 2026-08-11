// PROBLEM: none
fun test(int: Int?): Unit = <caret>if (int == 5) {
} else {
    foo()
}

fun foo() {}
