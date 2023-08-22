// "Make test suspend" "false"
// DISABLE-ERRORS
// ACTION: Convert to single-line lambda
// ACTION: Do not show implicit receiver and parameter hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// ACTION: Move lambda argument into parentheses
// ACTION: Specify explicit lambda signature
suspend fun foo() {}

fun bar(f: () -> Unit) {
}

fun test() {
    bar {
        <caret>foo()
    }
}