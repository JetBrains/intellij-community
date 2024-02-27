// MODE: return
val x = run {
    if (true) {
        1/*<# ^|[file.kt:28]run #>*/
    } else {
        0/*<# ^|[file.kt:28]run #>*/
    }
}