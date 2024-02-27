// MODE: return
val x = run hello@{
    if (true) {
    }

    run { // Two hints here
        when (true) {
            true -> 1/*<# ^|[file.kt:67]run #>*/
            false -> 0/*<# ^|[file.kt:67]run #>*/
        }
    }/*<# ^|[file.kt:34]hello #>*/
}