// "Import" "false"
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Create extension function 'Base.foo'
// ACTION: Create member function 'Base.foo'
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

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
    b.<caret>foo() // no import: there no foo in Base
}
