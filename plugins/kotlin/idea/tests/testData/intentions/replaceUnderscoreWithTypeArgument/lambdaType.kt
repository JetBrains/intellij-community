// AFTER-WARNING: Variable 'z' is never used

fun foo() {
    val z = bar<<caret>_> { it * 2 }
}

fun <T> bar(a: (Int)->T): T = a(1)