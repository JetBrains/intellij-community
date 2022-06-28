// "Create object 'Foo'" "false"
// ACTION: Create local variable 'Foo'
// ACTION: Create parameter 'Foo'
// ACTION: Create property 'Foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ACTION: Split property declaration
// ERROR: Unresolved reference: Foo
open class Cyclic<E : Cyclic<E>>

fun test() {
    val c : Cyclic<*> = <caret>Foo
}