// "Remove useless elvis operator" "true"
fun foo(a: String?) {
    val b = a <caret>?: null
}
