fun someFun() {
    val range = 0/*<# ≤ #>*/../*<# ≤ #>*/10
    for (i in 0/*<# ≤ #>*/../*<# ≤ #>*/5) {}
    for (i in 0/*<# ≤ #>*/..<5) {}
    for (i in 5/*<# ≤ #>*/ until /*<# < #>*/index) {}
    for (i in 5/*<# ≥ #>*/ downTo /*<# ≥ #>*/0) {}
}