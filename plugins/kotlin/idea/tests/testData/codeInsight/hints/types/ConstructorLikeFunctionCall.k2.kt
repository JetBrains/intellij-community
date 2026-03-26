// MODE: all
package a

val f1 = Foo()
val f2/*<# : |[b.Foo:kotlin.fqn.class]Foo #>*/ = foo()

fun Foo(): b.Foo = b.Foo()
fun foo(): b.Foo = b.Foo()