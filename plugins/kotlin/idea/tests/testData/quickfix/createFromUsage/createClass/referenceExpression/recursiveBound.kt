// "Create object 'Foo'" "false"
// ERROR: Unresolved reference: Foo
// IGNORE_K2
// K2_ERROR: UNRESOLVED_REFERENCE
open class Cyclic<E : Cyclic<E>>

fun test() {
    val c : Cyclic<*> = <caret>Foo
}