// "Remove redundant calls of the conversion method" "false"
// WITH_STDLIB
fun foo(s: String?) {
    val t: String = s.toString()<caret>
}