// "Create class 'Foo'" "true"
// ERROR: Unresolved reference: Foo
/* IGNORE_K2 */
fun <U> test(u: U) {
    val a = J(u).Foo(u)
}