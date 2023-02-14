// PROBLEM: none
fun test(int: Int?): Unit = when (int) {
    5 -> {
        foo()
    }
    else -> {
        Unit<caret>
    }
}

fun foo() {}
