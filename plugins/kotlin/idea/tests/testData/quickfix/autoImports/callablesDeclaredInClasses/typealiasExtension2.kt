// "Import" "true"
package p

object Obj
typealias ObjAlias = Obj

open class Foo {
    fun Obj.ext() {}
}

object FooObj : Foo()

fun usage() {
    ObjAlias.<caret>ext()
}
