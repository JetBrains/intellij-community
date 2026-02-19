// MODE: return
val x = run {
    when (true) {
        true -> 1/*<# ^|[When.kt:28]run #>*/
        false -> 0/*<# ^|[When.kt:28]run #>*/
    }
}