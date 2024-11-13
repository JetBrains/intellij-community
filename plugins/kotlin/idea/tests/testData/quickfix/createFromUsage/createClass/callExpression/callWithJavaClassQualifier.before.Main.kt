// "Create class 'Foo'" "true"
// ERROR: Unresolved reference: Foo
/* IGNORE_K2 */
fun test() {
    val a = J.<caret>Foo(2)
}