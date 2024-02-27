// MODE: return
fun bar() {
    var test = 0
    run {
        test
        test++/*<# ^|[file.kt:53]run #>*/
    }

    run {
        test
        ++test/*<# ^|[file.kt:98]run #>*/
    }
}