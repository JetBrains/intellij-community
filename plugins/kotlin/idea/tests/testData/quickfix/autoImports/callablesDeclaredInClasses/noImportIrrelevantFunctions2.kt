// "Import" "false"
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Create extension function 'Base.defaultFun'
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

fun usage(b: Base) {
    b.<caret>defaultFun() // no import: there is no defaultFun in Base
}
