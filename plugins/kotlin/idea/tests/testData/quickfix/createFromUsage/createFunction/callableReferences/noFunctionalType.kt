// "Create function 'foo'" "false"
// ACTION: Add 'n =' to argument
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo
fun bar(n: Int) = "$n"

fun consume(s: String) {}

fun test() {
    consume(bar(::<caret>foo))
}