// "Create function 'bar'" "true"
// ERROR: Unresolved reference: bar
/* IGNORE_K2 */

fun foo(foo: Foo) {
    val s = foo.eval()
    <caret>bar(s)
}