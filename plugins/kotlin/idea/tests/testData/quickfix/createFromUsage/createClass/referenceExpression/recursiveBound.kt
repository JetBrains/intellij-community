// "Create object 'Foo'" "false"
// ERROR: Unresolved reference: Foo
open class Cyclic<E : Cyclic<E>>

fun test() {
    val c : Cyclic<*> = <caret>Foo
}