// "Create object 'Foo'" "false"
// ERROR: Unresolved reference: Foo
// IGNORE_K2
open class Cyclic<E : Cyclic<E>>

fun test() {
    val c : Cyclic<*> = <caret>Foo
}