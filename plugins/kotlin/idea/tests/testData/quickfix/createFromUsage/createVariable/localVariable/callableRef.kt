// "Create local variable 'foo'" "false"
// ERROR: Unresolved reference: foo
fun test(f: (Int) -> Int) {}

fun refer() {
    val v = test(::<caret>foo)
}
