// "Import" "false"
// ACTION: Create extension function 'Base.defaultFun'
// ACTION: Create function 'defaultFun'
// ACTION: Create member function 'Base.defaultFun'
// ACTION: Rename reference
// ERROR: Unresolved reference: defaultFun
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
    <caret>defaultFun() // no import: there is no defaultFun in Base
}
