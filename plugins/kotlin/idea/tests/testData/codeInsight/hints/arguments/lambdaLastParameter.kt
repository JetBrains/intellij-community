fun foo(index: Int, action: (String) -> String) {}

fun m() {
    foo(/*<# [lambdaLastParameter.kt:8]index| = #>*/0) { "" }
}