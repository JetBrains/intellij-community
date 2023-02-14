// "Import" "false"
// ACTION: Create extension function 'Base.foo'
// ACTION: Create function 'foo'
// ACTION: Create member function 'Base.foo'
// ERROR: Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: <br>public final fun Other.foo(): Unit defined in p.Base
package p

class Other

open class Base {
    fun Other.foo() {}
}

interface SomeInterface {
    fun Other.defaultFun() {}
}

object ObjBase : Base(), SomeInterface

fun Base.usage() {
    <caret>foo() // no import: there is no foo in Base
}
