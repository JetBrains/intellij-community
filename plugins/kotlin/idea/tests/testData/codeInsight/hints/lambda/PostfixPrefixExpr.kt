// MODE: return
fun bar() {
    var test = 0
    run {
        test
        test++/*<# ^|[PostfixPrefixExpr.kt:53]run #>*/
    }

    run {
        test
        ++test/*<# ^|[PostfixPrefixExpr.kt:98]run #>*/
    }
}