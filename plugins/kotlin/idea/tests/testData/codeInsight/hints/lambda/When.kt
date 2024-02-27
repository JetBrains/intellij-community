// MODE: return
val x = run {
    when (true) {
        true -> 1/*<# ^|[file.kt:28]run #>*/
        false -> 0/*<# ^|[file.kt:28]run #>*/
    }
}