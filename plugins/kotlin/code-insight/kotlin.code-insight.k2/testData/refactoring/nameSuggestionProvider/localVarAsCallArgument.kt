// SUGGESTED_NAMES: baz, paramBar, s
// SUGGESTED_NAMES_K2: baz, paramBar, s, str, string, text
fun foo(paramBar: String) {

}

fun test() {
    val <caret>baz = ""
    foo(baz)
}