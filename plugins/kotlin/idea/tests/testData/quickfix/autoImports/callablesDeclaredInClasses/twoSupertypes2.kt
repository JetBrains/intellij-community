// "Import" "true"
package p

class Other

open class Base {
    fun Other.foo() {}
}

interface SomeInterface {
    fun Other.defaultFun() {}
}

object ObjBase : Base(), SomeInterface

fun usage(c: Other) {
    c.<caret>defaultFun()
}
