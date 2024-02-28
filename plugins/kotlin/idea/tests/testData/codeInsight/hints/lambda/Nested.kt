// MODE: return
val x = run hello@{
    if (true) {
    }

    run { // Two hints here
        when (true) {
            true -> 1/*<# ^|[Nested.kt:67]run #>*/
            false -> 0/*<# ^|[Nested.kt:67]run #>*/
        }
    }/*<# ^|[Nested.kt:34]hello #>*/
}