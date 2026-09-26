// INTENTION_TEXT: "Add parameter name to all callsites"

fun foo(s: String, <caret>f: () -> Unit) {
}

fun test() {
    foo("", {
        val lambda = {}
        foo("", lambda)
    })
}