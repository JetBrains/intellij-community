val range = 0<# ≤ #>..<# ≤ #>10

fun foo() {
    for (index in 0<# ≤ #> .. <# ≤ #>100) {}
    for (index in 'a'<# ≤ #> .. <# ≤ #>'z') {}
    for (index in 0L<# ≤ #> .. <# ≤ #>100L) {}
    for (index in 0<# ≤ #> until <# < #>100) {}
    for (index in 'a'<# ≤ #> until <# < #>'z') {}
    for (index in 0L<# ≤ #> until <# < #>100L) {}
    for (index in 100<# ≥ #> downTo <# ≥ #>0) {}
    for (index in 'z'<# ≥ #> downTo <# ≥ #>'a') {}
    for (index in 100L<# ≥ #> downTo <# ≥ #>0L) {}
    for (i in 0 until 0<# ≤ #>..<# ≤ #>5 ) {}
    for (i in 1<# ≤ #> until <# < #>10 step 2) {}
    run {
        val left: Short = 0
        val right: Short = 10

        for (i in left<# ≤ #> .. <# ≤ #>right) {}
        for (i in left<# ≤ #> until <# < #>right) {}
        for (index in right<# ≥ #> downTo <# ≥ #>left) {}
    }

    for (index in someVeryVeryLongLongLongLongFunctionName(0)<# ≤ #> .. <# ≤ #>someVeryVeryLongLongLongLongFunctionName(100)) {}
}

private infix fun Int.until(intRange: IntRange): IntRange = TODO()

private fun someVeryVeryLongLongLongLongFunctionName(x: Int): Int = x

private fun check(x: Int, y: Int) {
    val b = x in 8<# ≤ #>..<# ≤ #>9
    if (x in 7<# ≤ #>..<# ≤ #>9 && y in 5<# ≤ #>..<# ≤ #>9) {
    }
}
