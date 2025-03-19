// MODE: return
val x = run {
    if (true) {
        1/*<# ^|[If.kt:28]run #>*/
    } else {
        0/*<# ^|[If.kt:28]run #>*/
    }
}