// "Import" "false"
// ACTION: Rename reference
// ERROR: Unresolved reference: genericExt
package p

open class Base<T> {
    fun T.genericExt() {}
}

object Obj : Base<Int>()

fun usage() {
    "hello".<caret>genericExt()
}
