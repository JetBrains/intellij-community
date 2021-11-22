val range = 0<# ≤ #>..<# ≤ #>10

fun foo() {
    for (index in 0<# ≤ #> .. <# ≤ #>100) {}
    for (index in 0<# ≤ #> until <# < #>100) {}
    for (index in 100<# ≥ #> downTo <# ≥ #>0) {}
    for (i in 0 until 0<# ≤ #>..<# ≤ #>5 ) {}
    for (i in 1<# ≤ #> until <# < #>10 step 2) {}
    for (index in someVeryVeryLongLongLongLongFunctionName(0)<# ≤ #> .. <# ≤ #>someVeryVeryLongLongLongLongFunctionName(100)) {}
}

private infix fun Int.until(intRange: IntRange): IntRange = TODO()

private fun someVeryVeryLongLongLongLongFunctionName(x: Int): Int = x

private fun check(x: Int, y: Int) {
    val b = x in 8<# ≤ #>..<# ≤ #>9
    if (x in 7<# ≤ #>..<# ≤ #>9 && y in 5<# ≤ #>..<# ≤ #>9) {
    }
}
