// PROBLEM: none
fun invalidate(condition: (String, String) -> Boolean) {

}

fun foo() {
    invalidate { _<caret>, v -> v != "" }
}
