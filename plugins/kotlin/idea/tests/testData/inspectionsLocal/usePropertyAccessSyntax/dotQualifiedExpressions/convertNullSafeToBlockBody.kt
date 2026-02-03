// FIX: Use property access syntax
// IGNORE_K1
/* For K1, the conversion returns
fun a(foo: Foo?): Unit? {
    return foo?.setFoo(1)
}
And such a setter after return cannot be replaced.
*/

fun a(foo: Foo?) = foo?.<caret>setFoo(1)